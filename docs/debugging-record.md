# デバッグ記録: close例外で本体例外がすり替わる請求CSV取込

## 目的

Java 21で、手動`finally`による`AutoCloseable#close()`の例外が本体処理の例外を置き換え、請求CSV取込の業務分類が誤る理由を、実行可能な最小例で確認する。

> 契約: `invoiceId,amount\nINV-001,` に対して、主失敗を`INVALID_INVOICE`として返し、監査ログのclose例外はsuppressed例外として取得できる。バグ状態では主失敗が`AUDIT_CLOSE_FAILED`になる。

## 実行環境と再現境界

| 項目 | 内容 |
| --- | --- |
| 言語処理系 | JDK 21 |
| 難易度プロファイル | 実践・上級。CSV入力の業務分類、リソースクローズ、例外伝播の三つの観測を時系列で比較するため。 |
| ビルド・テスト方法 | `./run-tests.sh`。`javac --release 21`と`java -ea`のみ。 |
| 使用する依存関係 | Java標準ライブラリのみ。 |
| 使用しないもの | Spring、ORM、DB、ファイルI/O、外部CSVライブラリ、外部テストフレームワーク。 |
| 公開境界 | `InvoiceImportService#importCsv(String)` |
| 最終観測 | `ImportResult`の業務分類、主例外型、suppressed例外型、`AuditTrail#isClosed()`。 |
| 決定性の確保 | 固定CSVと、close時に必ず`AuditCloseException`を送出するテスト用監査ログを使う。 |

この境界を選んだ理由は、Web・バッチ・永続化フレームワークを介さず、Javaの例外伝播と`AutoCloseable`の振る舞いを、業務上の戻り値とリソース状態の両方で直接観測できるためである。

## 最初に観測した事実

| 観測順 | 事実 | 得られた証拠 |
| --- | --- | --- |
| 1 | 入力はamountが空の`invoiceId,amount\nINV-001,`であり、監査ログはclose時に必ず例外を送出する。 | `InvoiceImportServiceTest`のArrange。 |
| 2 | バグ状態の契約テストは`expected: INVALID_INVOICE but was: AUDIT_CLOSE_FAILED`で失敗した。 | `docs/01-bug-reproduction.log`。 |
| 3 | バグ状態のWARNINGログは`primary=AuditCloseException, suppressed=0`だった。 | 同ログ。 |
| 4 | JDBは`InvoiceCsvParser.validate`で`InvalidInvoiceException`が発生した後、`FailingAuditTrail.close`で`AuditCloseException`が発生する順番を停止した。 | `jdb`の例外停止とスタックフレーム。 |
| 5 | 修正後のWARNINGログは`primary=InvalidInvoiceException, suppressed=1`であり、同じテストが成功した。 | `docs/02-fixed-verification.log`。 |

バグ状態のコミットは`13d0fef`である。`./run-tests.sh`を実行すると、設定、依存解決、コンパイルではなく、次の意図したアサーション差分が確認できる。

```text
AssertionError: expected: INVALID_INVOICE but was: AUDIT_CLOSE_FAILED
```

## コードリーディングとデバッガーの観測点

バグ状態の`InvoiceImportService#importCsv`は、`InvoiceCsvParser#validate`を内側の`try`で実行し、`finally`で`auditTrail.close()`を直接呼ぶ。その外側に`catch (Exception failure)`がある。この構造では、パーサーが送出した`InvalidInvoiceException`の後に`close()`が`AuditCloseException`を送出すると、外側catchは後者を受け取る。

| 停止位置 | 観測するもの | バグ状態で確認したこと |
| --- | --- | --- |
| `InvoiceCsvParser.validate`の例外送出行 | 例外型と呼び出し元 | 最初に`InvalidInvoiceException`が発生する。 |
| `FailingAuditTrail.close`の例外送出行 | 例外型とスタック | `finally`から`AuditCloseException`が発生する。 |
| `InvoiceImportService`の外側catch | ログの`primary`と`suppressed`数 | 外側catchが`AuditCloseException`を主例外として扱い、suppressed数は0になる。 |

JDBの例外停止では、`InvalidInvoiceException`が`InvoiceCsvParser.validate`で発生した後、`AuditCloseException`が`FailingAuditTrail.close`で発生した。加えて、実行ログが`primary=AuditCloseException`を示すため、入力検証の失敗が起きなかったのではなく、後続のclose例外に置き換わったと結論づけられる。

## 競合仮説と検証

| 仮説 | 予測 | 検証 | 結果 |
| --- | --- | --- | --- |
| CSVパーサーが不正なamountを検出していない | `InvalidInvoiceException`が発生しない。 | `InvoiceCsvParser.validate`でJDBを停止する。 | 除外。`InvalidInvoiceException`が最初に発生した。 |
| 監査ログがcloseされず、リソース管理が欠けている | `isClosed()`がfalseになる。 | 同じ契約テストで`isClosed()`を確認する。 | 除外。バグ状態でもcloseは実行される。 |
| close例外が本体例外を上書きしている | パーサー例外の後にclose例外が発生し、外側catchがclose例外を受け取る。 | JDBの停止順とWARNINGログの主例外型を比較する。 | 支持。`AuditCloseException`が主失敗として返る。 |

## 確定した原因

バグ状態では、本体処理とクローズ処理を手動の`try`と`finally`で記述した。`finally`で`close()`が例外を送出すると、先に発生していた本体例外よりも`finally`の例外が呼び出し元へ伝わる。Oracleの公式チュートリアルも、通常の`finally`では本体とcloseの両方が例外を送出した場合に`finally`側の例外が送出される一方、try-with-resourcesでは本体側の例外が送出され、クローズ側の例外はsuppressedになると説明している。[1]

このラボで直接観測した事実は、パーサー例外、close例外、外側catchが返した業務分類の順序である。try-with-resourcesでsuppressed例外を取得できることは、Java言語仕様と`Throwable#getSuppressed()`のAPIで裏づける。[2] [3]

## 最小修正

手動のネストした`try`と`finally`を、リソースを宣言するtry-with-resourcesへ置き換えた。

```java
try (AuditTrail auditTrail = auditTrailFactory.open()) {
    auditTrail.record("IMPORT_STARTED");
    parser.validate(csv);
    auditTrail.record("IMPORT_ACCEPTED");
    return ImportResult.successful();
} catch (Exception failure) {
    return ImportResult.rejected(failure);
}
```

この変更は、例外の分類ロジック、CSVパーサー、`AuditTrail`のAPI、テストの期待値を変更しない。修正コミットは`e481538`である。

## 回帰保証

| 守ること | テストまたは診断 | 修正後の結果 |
| --- | --- | --- |
| 不正CSVを`INVALID_INVOICE`として分類する | `InvoiceImportServiceTest` | 成功。 |
| 主例外が`InvalidInvoiceException`である | 同テストの`primaryFailureType`アサーション | 成功。 |
| close例外を失わずsuppressedとして残す | 同テストの`suppressedFailureTypes`アサーション | 成功。 |
| 例外時にも監査ログをcloseする | 同テストの`isClosed()`アサーション | 成功。 |
| 取込開始イベントを維持する | 同テストの`events()`アサーション | 成功。 |

固定済みの状態で`./run-tests.sh`を実行し、`ALL TESTS PASSED`を確認した。

## 再現手順

```bash
# 修正済み状態を検証する
./run-tests.sh

# バグ状態を確認する。作業中の変更は先に退避する
git switch --detach 13d0fef
./run-tests.sh
# AssertionError: expected: INVALID_INVOICE but was: AUDIT_CLOSE_FAILED

# 修正済み状態へ戻る
git switch main
./run-tests.sh
```

## スコープと注意点

このラボは、単一の`AutoCloseable`で、本体例外とクローズ例外が同じ実行経路で発生する条件に限って再現・修正を確認した。複数リソースのクローズ順、クローズ失敗の再試行、例外を監視基盤へ転送する設計、実ファイルやネットワークI/Oの障害、フレームワークの例外変換には同じ結論を自動的に拡張しない。

## References

[1] [The try-with-resources Statement — The Java Tutorials](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)

[2] [Java Language Specification, §14.20.3: The try-with-resources Statement](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3)

[3] [Java SE 21 API: `Throwable#getSuppressed()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html#getSuppressed())
