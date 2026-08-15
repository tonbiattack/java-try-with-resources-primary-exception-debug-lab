# 題材重複調査レポート

> 生成日時: `2026-08-15 11:43 UTC`

## 候補題材

| 項目 | 内容 |
| --- | --- |
| 言語 | Java |
| 題材名 | try-with-resourcesで本体例外を保持する |
| 契約 | 壊れた請求CSVの業務エラーを返すべきだが、finallyでclose例外が上書きしてAUDIT_CLOSE_FAILEDになる |
| 検索語 | Java, try-with-resources, finally, suppressed, AutoCloseable, exception, close |
| カタログ | `/home/ubuntu/work/repository-catalog/data/repositories.json`（453件） |

## 自動検索の結論

**近接候補あり（要比較）**。同じ領域または用語を含む既存題材があります。原因、実境界、観測契約、最小修正を比較し、重複しない差分を記録してから作成してください。

この結果は語彙的な一次スクリーニングであり、重複の最終判定ではありません。候補がある場合は、該当リポジトリのREADME、失敗するテスト、原因、観測契約を比較してください。

## 近接候補

| リポジトリ | スコア | 共通語 | 言語 | 内容 |
| --- | --- | --- | --- | --- |
| [spring-batch-chank-on-tasklet](https://github.com/tonbiattack/spring-batch-chank-on-tasklet) | 4 | csv | Java | Spring Batch Tasklet の中で Chank を使用する |

## 手動比較の記録

| 比較対象 | 既存題材の原因・境界・契約 | 今回の差分 | 判定 |
| --- | --- | --- | --- |
| `spring-batch-chank-on-tasklet` | 直接原因はSpring BatchのTaskletとChunkモデルの選択であり、境界はCSVをDBへ連携するバッチジョブである。READMEはリカバリーと中間コミットを扱う。 | 直接原因は標準Javaの`finally`で本体例外が`close()`例外に置き換わること。境界は単一CSV入力を業務結果へ変換する公開メソッドで、主例外の種類とsuppressed例外を最終観測する。 | 重複なし |

## 作成可否

### 結論

作成する。近接候補はCSVという入力形式が共通するだけで、直接原因、実境界、観測契約、最小修正の四軸すべてが異なる。今回の教材では、`finally`による例外の上書きを`try-with-resources`へ置換し、本体例外を保持したまま`Throwable#getSuppressed()`でクローズ失敗を追跡する契約を扱う。

- [x] カタログは更新済みである。
- [x] 近接候補のREADMEまたは主要テストを確認した。
- [x] 原因、実境界、観測契約、最小修正の差分を記録した。
- [x] 同じ失敗を別名で再実装していない。
- [x] `language-agnostic-debugging-lab`の品質ゲートを満たす計画がある。
