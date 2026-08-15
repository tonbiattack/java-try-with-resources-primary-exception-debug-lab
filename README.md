# 本体例外がclose例外にすり替わる請求CSV取込をデバッグする

壊れた請求CSVを受け取ったとき、本来は`INVALID_INVOICE`を返すべきなのに、監査ログの`close()`で発生した例外が本体例外を上書きし、`AUDIT_CLOSE_FAILED`を返してしまう不具合を再現する標準Javaの教材です。アプリケーションフレームワーク、外部サービス、外部テストライブラリは使いません。

## この題材で守る契約

> `invoiceId,amount\nINV-001,` のように必須列を欠く請求CSVでは、主失敗を`INVALID_INVOICE`として返し、クローズ失敗は`Throwable#getSuppressed()`で追跡可能にする。

バグ状態では、手動の`finally`で`AuditTrail#close()`を呼ぶため、`InvalidInvoiceException`より後に発生した`AuditCloseException`が外側のcatchへ伝わります。結果として、業務上重要な「請求CSVが不正」という分類が失われます。

| 観測項目 | バグ状態 | 修正後 |
| --- | --- | --- |
| 主失敗の分類 | `AUDIT_CLOSE_FAILED` | `INVALID_INVOICE` |
| 主失敗の例外型 | `AuditCloseException` | `InvalidInvoiceException` |
| suppressed例外 | 0件 | `AuditCloseException`が1件 |
| 監査ログのクローズ | 実行される | 実行される |

## 最短の開始手順

JDK 21で次を実行します。`javac`と`java -ea`だけで、全ソースのコンパイルと契約テストを実行します。

```bash
./run-tests.sh
```

修正済みの状態では、次のログと`ALL TESTS PASSED`を確認できます。

```text
取込失敗を返します: primary=InvalidInvoiceException, suppressed=1
ALL TESTS PASSED
```

## バグを再現する

バグ状態はコミット`13d0fef`、最小修正はコミット`e481538`に保存しています。作業中の変更がないことを確認してから、次を実行します。

```bash
git switch --detach 13d0fef
./run-tests.sh
# AssertionError: expected: INVALID_INVOICE but was: AUDIT_CLOSE_FAILED

git switch main
./run-tests.sh
# ALL TESTS PASSED
```

## 調査の順番

| 段階 | 観測するもの | 分かること |
| --- | --- | --- |
| 契約テスト | `InvoiceImportServiceTest` | 正しい業務分類、主例外、suppressed例外、close状態を同時に確認できる |
| アプリケーションログ | `InvoiceImportService`のWARNING | バグ状態で外側catchが`AuditCloseException`を受け取る |
| コードリーディング | `finally`と`catch` | 本体例外の後に`close()`が実行される経路を追える |
| デバッガー | パーサー送出点、`finally`、外側catch | 例外オブジェクトがどの時点で置き換わるかを確認できる |
| 修正後の同一テスト | `try-with-resources`への変更後 | 本体例外が主例外として残り、close例外がsuppressedへ移る |

詳細な仮説、観測ログ、デバッガーの停止位置、修正の範囲は[デバッグ記録](docs/debugging-record.md)を参照してください。

## 構成

```text
src/main/java/com/example/tryresources/
├── InvoiceImportService.java       # 取込の公開境界
├── InvoiceCsvParser.java           # 不正CSVを検出する最小パーサー
├── AuditTrail.java                 # AutoCloseableの監査ログ境界
└── ImportResult.java               # 業務分類と例外情報を持つ結果
src/test/java/com/example/tryresources/
└── InvoiceImportServiceTest.java   # フレームワークなしの契約テスト
docs/
├── 01-bug-reproduction.log         # バグ状態の実行記録
├── 02-fixed-verification.log       # 修正後の実行記録
├── debugging-record.md             # 調査・原因・修正・回帰保証
├── novelty-report.md               # 既存題材との重複調査
└── topic-brief.md                  # 実装前に固定した契約と仮説
```

## 前提条件

| 項目 | バージョンまたは条件 |
| --- | --- |
| JDK | 21 |
| ビルド・テストツール | `javac`と`java`（JDK同梱） |
| 外部サービス | 不要 |
| 外部依存 | 不要 |

## スコープ

この教材は、一つの`AutoCloseable`を手動`finally`で閉じたときに、本体例外とクローズ例外が競合する条件だけを扱います。実ファイルI/O、複数リソースのクローズ順、例外の再試行、監視への通知、Spring Batchなどのフレームワーク統合についての一般的な推奨を示すものではありません。

## References

[1] [The try-with-resources Statement — The Java Tutorials](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)

[2] [Java Language Specification, §14.20.3: The try-with-resources Statement](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3)

[3] [Java SE 21 API: `Throwable#getSuppressed()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Throwable.html#getSuppressed())
