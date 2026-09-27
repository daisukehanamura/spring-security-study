# spring-security-study

Spring Security / OAuth2（Spring Authorization Server）/ MCP サーバを
**動くサンプル + 解説 md** で学ぶための個人用リポジトリ。

Java と Spring の基礎は前提にしつつ、Security 特有の「なぜこう書くのか」と
「どこでハマるのか」に寄せて書いています。

## 前提環境

- JDK 17 以上（確認済み: Microsoft OpenJDK 17.0.9 / Amazon Corretto 21）
- Maven は不要（各モジュールに Maven Wrapper `./mvnw` 同梱）
- Docker（03 の SAML IdP = Keycloak を動かすときだけ必要）

各モジュールは**独立した Maven プロジェクト**です（Boot のバージョンが違うため）。

## モジュール一覧

| # | モジュール | テーマ | Boot / Security | ポート | 状態 |
|---|---|---|---|---|---|
| 01 | [`01-basics-boot27`](01-basics-boot27) | **2.7 時点の書き方**（Adapter 方式 vs コンポーネント方式） | 2.7.18 / 5.8 | 8080 | ✅ 完成（テスト 16 件 green） |
| 02 | [`02-basics-boot3`](02-basics-boot3) | **3.x の書き方** + **認可（ROLE 制御）** | 3.5.16 / 6.5 | 8081 | ✅ 完成（テスト 20 件 green） |
| 03 | `03-saml-form-hybrid` | **通常ログインと SAML の共存** | 3.5.16 / 6.5 | 8082 | 🚧 作りかけ（pom のみ・設計は下記） |
| 04 | `04-oauth2-authorization-server` | **OAuth2 / OIDC 認可サーバ** | 3.5.16 / SAS 1.5 | 9000/8080/8090 | ⬜ 未着手 |
| 05 | `05-mcp-server-spring` | **MCP サーバ（Spring AI）** | 3.5.16 / Spring AI 1.1.8 | 8090 | ⬜ 未着手 |

## 読む順番

1. [`docs/00-architecture.md`](docs/00-architecture.md) … フィルタチェーンと登場人物。**まずここ**
2. [`01-basics-boot27`](01-basics-boot27) を動かす → 2 つの設定クラスを読み比べる
3. [`docs/01-migration-27-to-3x.md`](docs/01-migration-27-to-3x.md) … 2.7 → 3.x の対応表と移行手順
4. [`02-basics-boot3`](02-basics-boot3) を動かす → `./mvnw test` でルールの挙動を確認
5. [`docs/02-authorization.md`](docs/02-authorization.md) … ROLE / authority の設計指針
6. 03（SAML 共存）→ 04（OAuth2）→ 05（MCP）

## すぐ動かす

```bash
# 2.7 の書き方（コンポーネント方式）
cd 01-basics-boot27 && ./mvnw spring-boot:run
# 2.7 の旧書き方（WebSecurityConfigurerAdapter）
cd 01-basics-boot27 && ./mvnw spring-boot:run -Dspring-boot.run.profiles=adapter
# 3.x の書き方 + 認可いろいろ
cd 02-basics-boot3  && ./mvnw spring-boot:run

# 認可ルールをテストで確認（挙動の仕様書として読める）
cd 01-basics-boot27 && ./mvnw test
cd 02-basics-boot3  && ./mvnw test
```

ログインユーザーは各モジュールの README 参照（基本 `user` / `admin`、パスワードは `password`）。

## ドキュメント

| ファイル | 内容 |
|---|---|
| [`docs/00-architecture.md`](docs/00-architecture.md) | フィルタチェーン、認証/認可の流れ、登場人物の早見表 |
| [`docs/01-migration-27-to-3x.md`](docs/01-migration-27-to-3x.md) | API 対応表、移行手順、落とし穴 |
| [`docs/02-authorization.md`](docs/02-authorization.md) | ROLE / authority 設計、URL・メソッド認可、401 と 403 |
| [`docs/99-progress.md`](docs/99-progress.md) | **作業の進捗と再開メモ**（続きをやるとき最初に読む） |

## 続きをやるとき

[`docs/99-progress.md`](docs/99-progress.md) に「何が終わっていて、次に何をするか」
「調査済みの前提（バージョン・リポジトリ・設計方針）」をまとめてあります。
