# 題材企画: close例外で本体例外がすり替わる請求CSV取込をデバッグする

## 対象

| 項目 | 内容 |
| --- | --- |
| 対象言語 | Java 21 |
| 対象読者 | Javaの例外処理と`finally`を実務で使う中級者 |
| 難易度プロファイル | 実践・上級 |
| 選定理由 | CSV取込の失敗理由、監査ログのクローズ、例外の伝播が一見もっともらしく連続するため、ログ・テスト・デバッガーを使った仮説比較が必要になる。 |
| 実行基盤 | `javac`と`java -ea`のみ。外部依存は使わない。 |
| フレームワーク非依存性 | Web、DI、ORM、テストフレームワークを使わず、`AutoCloseable`、`finally`、`Throwable#getSuppressed()`だけで再現する。 |

## 学習する契約

> 入力 `必須列を欠いた請求CSV` に対して、期待する `INVALID_INVOICEを主失敗として返し、監査ログclose例外をsuppressedに保持する結果` は、バグ状態では `AUDIT_CLOSE_FAILEDを主失敗として返し、請求CSVが不正である事実を失う結果` になる。

### 対象の直接原因

`finally`で`AutoCloseable#close()`を直接呼び、本体の`InvalidInvoiceException`より後に発生した`AuditCloseException`が外側のcatchへ伝わるため、本体例外が置き換わること。

### 対象外

実ファイルI/O、Spring Batch、DBトランザクション、CSVライブラリの解析仕様、監査ログの再試行、複数リソースのクローズ順、例外を監視システムへ送る運用は扱わない。

## 再現設計

| 要素 | 決定 |
| --- | --- |
| 公開境界 | `InvoiceImportService#importCsv(String)` |
| 入力・初期状態 | `invoiceId,amount\nINV-001,` と、close時に必ず例外を送出するメモリ上の監査ログ |
| Redの観測 | `expected: INVALID_INVOICE but was: AUDIT_CLOSE_FAILED` |
| 最終観測 | 主失敗の例外種別、suppressed例外、監査ログがclosedである状態を独立に検証する。 |
| 決定性 | 時刻、スレッド、外部I/O、sleepを使わず、テスト用の`FailingAuditTrail`が必ずclose例外を送出する。 |
| 固定状態の検証コマンド | `./run-tests.sh` |
| バグ状態の確認コマンド | `./run-tests.sh` |

## 仮説

| 仮説 | どう検証または除外するか |
| --- | --- |
| CSVの解析が不正入力を検出していない | 解析器のINFOログと、解析器が送出する例外クラスを確認する。 |
| 監査ログをcloseしていない | `FailingAuditTrail#isClosed()`を回帰テストで確認する。 |
| `finally`のclose例外が本体例外を上書きしている | `finally`前後と外側catchにブレークポイントを置き、catchした例外クラスとsuppressed配列を観測する。 |

## 予定する履歴

| 順序 | コミットの目的 | 期待する状態 |
| --- | --- | --- |
| 1 | 請求CSV取込の失敗を再現する | 対象テストが`INVALID_INVOICE`と`AUDIT_CLOSE_FAILED`の差分で失敗する。 |
| 2 | 本体例外を保持するよう修正する | 同じ検証が成功し、主例外とsuppressed例外の両方を確認できる。 |
