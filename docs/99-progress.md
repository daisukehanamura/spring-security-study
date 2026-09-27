# 進捗と再開メモ

最終更新: 2026-09-27

## 完成しているもの

| モジュール | 状態 | 検証 |
|---|---|---|
| `01-basics-boot27` | ✅ 完成 | `./mvnw test` → 16 件 green（Adapter 方式 / コンポーネント方式の両プロファイル） |
| `02-basics-boot3` | ✅ 完成 | `./mvnw test` → 20 件 green |
| `docs/00-architecture.md` | ✅ | — |
| `docs/01-migration-27-to-3x.md` | ✅ | — |
| `docs/02-authorization.md` | ✅ | — |

## 次にやること（優先順）

### 1. `03-saml-form-hybrid`（作りかけ: `pom.xml` だけ存在）

**ゴール**: 同じアプリで「ID/パスワードのフォームログイン」と「SAML SSO」を共存させ、
どちらで入っても同じ認可ルールが効くことを確認する。

実装予定のファイル:

```
03-saml-form-hybrid/
├── pom.xml                                   ✅ 済（Shibboleth リポジトリ定義込み）
├── docker/docker-compose.yml                 ⬜ Keycloak 26.7 を IdP として起動
├── docker/keycloak/realm-export.json         ⬜ SAML クライアント + ユーザー + role-list mapper
├── src/main/resources/application.yml        ⬜ フォームのみ（SAML 設定なしで起動できる）
├── src/main/resources/application-saml.yml   ⬜ saml プロファイルで Keycloak を IdP に
├── config/SecurityConfig.java                ⬜ formLogin + saml2Login/saml2Logout/saml2Metadata
├── config/SamlAuthoritiesConverter.java      ⬜ SAML の Role 属性 + 自社 DB のロール → GrantedAuthority
├── service/LocalUserRoleService.java         ⬜ NameID → ローカル権限の引き当て（JIT プロビジョニング相当）
├── web/PageController.java                   ⬜ /, /login（両方の入口）, /whoami, /admin/**, /403
└── src/test/java/...                         ⬜ フォーム側のテスト + ダミー IdP 登録での saml2 リダイレクト確認
```

**調査済みの前提（ここで時間を使わないように）**

- `spring-security-saml2-service-provider` は **OpenSAML 4.3.2** に依存し、
  それは **Maven Central に無い**。`pom.xml` に Shibboleth リポジトリを追加済み:
  `https://build.shibboleth.net/maven/releases/`
- Keycloak イメージは `quay.io/keycloak/keycloak:26.7`（26.7.4 が最新）を使う。
  `start-dev --import-realm` + `./keycloak:/opt/keycloak/data/import:ro` で realm を投入。
  管理者は `KC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD`（旧 `KEYCLOAK_ADMIN` ではない）。
- ポート: アプリ 8082 / Keycloak 8180。
- SP 側の既定 URL（registrationId を `keycloak` とした場合）
  - EntityID: `http://localhost:8082/saml2/service-provider-metadata/keycloak`
  - ACS: `http://localhost:8082/login/saml2/sso/keycloak`
  - 認証開始: `http://localhost:8082/saml2/authenticate/keycloak`
  - SLO: `http://localhost:8082/logout/saml2/slo`
  - → Keycloak の SAML クライアントの `clientId` は **SP の EntityID と同じ文字列**にする
- IdP のメタデータは URL 指定で取れる:
  `spring.security.saml2.relyingparty.registration.keycloak.assertingparty.metadata-uri=http://localhost:8180/realms/saml-demo/protocol/saml/descriptor`
  （証明書をベタ書きしなくて済む。ただし**起動時に取得するので Keycloak が先に立っていること**）
- そのため **SAML 設定は `saml` プロファイルに分離**し、デフォルト起動（Docker なし）では
  フォームログインだけで動くようにする。`SecurityConfig` では
  `ObjectProvider<RelyingPartyRegistrationRepository>` を受け取り、
  Bean が居るときだけ `http.saml2Login(...)` を適用する（これ自体が実務で使えるパターン）。
- 属性 → 権限のマッピングは `OpenSaml4AuthenticationProvider#setResponseAuthenticationConverter`
  で行う（Keycloak の `saml-role-list-mapper` が `Role` 属性を出す）。
- Keycloak 側は学習用に「クライアント署名の要求 = OFF」にして、SP 側の署名鍵設定を省略する。
  署名を有効にする手順は README に別途書く（証明書生成 → `signing.credentials`）。

**検証の仕方**: `docker compose up -d` → `curl http://localhost:8180/realms/saml-demo/protocol/saml/descriptor`
でメタデータが返ることを確認 → アプリを `saml` プロファイルで起動 → ブラウザで
`/login` から SAML ログイン。`/whoami` で `Saml2Authentication` になっていること、
`ROLE_ADMIN` が付いていることを確認する。

### 2. `04-oauth2-authorization-server`

マルチモジュール（親 pom + 3 アプリ）構成にする予定:

- `authorization-server`（:9000）… Spring Authorization Server。
  クライアント登録、`/oauth2/authorize`、`/oauth2/token`、`/oauth2/jwks`、`/userinfo`、
  `RegisteredClient` を 2 種（認可コード + PKCE のパブリッククライアント、`client_credentials`）
- `client`（:8080）… `spring-boot-starter-oauth2-client` で認可コードフローを実演
- `resource-server`（:8090）… `spring-boot-starter-oauth2-resource-server` で JWT 検証 + scope 認可
  （`SCOPE_read` / `SCOPE_write`、`JwtAuthenticationConverter` で権限変換）

SAS のバージョンは Boot 3.5 の依存管理に任せる（`spring-boot-starter-oauth2-authorization-server` を使えば 1.5.x）。
**注意**: Maven Central には SAS 7.x（Spring Security 7 / Boot 4 向け）も並んでいるので、
バージョンを自分で書かない（＝Boot の BOM に任せる）のが安全。

解説に入れる内容: OAuth2 の登場人物、認可コード + PKCE のシーケンス、
`scope` と ROLE の違い、アクセストークン/リフレッシュトークン/ID トークンの違い、
opaque token + イントロスペクションとの比較、JWT を使うときの失効の扱い。

### 3. `05-mcp-server-spring`

- Boot 3.5.16 + Spring AI **1.1.8**（`spring-ai-bom`）
- `spring-ai-starter-mcp-server-webmvc`（HTTP/SSE 系）で実装。`@Tool` を付けたメソッドを
  `MethodToolCallbackProvider` で登録するのが基本形
- STDIO 版（`spring-ai-starter-mcp-server`）との違い、Claude Code / Claude Desktop への
  登録方法（`claude mcp add` / 設定 JSON）も書く
- 発展: 04 の resource-server と組み合わせて MCP サーバを OAuth2 で保護する話に繋げられる
- Spring AI 2.0.x も出ているが、そちらは Boot 4 系前提なので 1.1.x を使う

## 決めてあること（ぶれないように）

- サンプルは**日本語コメント多めで、1 ファイル読めば意図が分かる**ように書く
- 「動く」ことをテストで示す（認可ルールはテストが仕様書になる）
- ハマりどころは**コメントと README の両方**に残す（実際に踏んだものは特に）
- モジュールは独立プロジェクト。共通の親 pom は作らない（Boot バージョンが違うため）
