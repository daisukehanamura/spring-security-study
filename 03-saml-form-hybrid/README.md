# 03 通常ログイン（ID/パスワード）と SAML SSO の共存 🚧 作成中

現状: `pom.xml` のみ（OpenSAML の依存解決に必要な Shibboleth リポジトリ定義込み）。
設計と調査済みの前提は [`../docs/99-progress.md`](../docs/99-progress.md) にまとめてあります。

## このモジュールで学ぶこと（予定）

- SAML2 の登場人物: SP（このアプリ）/ IdP（Keycloak）/ メタデータ / AuthnRequest / Assertion
- 同一アプリで `formLogin` と `saml2Login` を共存させる書き方
- SAML の属性（Keycloak の `Role`）を `GrantedAuthority` に変換する場所
- 「認証方式が 2 つあっても認可ルールは 1 つ」を実際に確認する
- IdP が無いと起動できない問題の回避（SAML 設定をプロファイルに分離する）

## ハマりどころ（先に分かっていること）

**OpenSAML 4.x は Maven Central に存在しない。** `spring-security-saml2-service-provider`
は `opensaml-saml-api:4.3.2` に依存しているため、次のリポジトリ定義が無いとビルドが失敗します。

```xml
<repositories>
  <repository>
    <id>shibboleth-releases</id>
    <url>https://build.shibboleth.net/maven/releases/</url>
  </repository>
</repositories>
```

社内 Nexus / Artifactory 経由でビルドしている環境では、このリポジトリのプロキシ設定が必要。
