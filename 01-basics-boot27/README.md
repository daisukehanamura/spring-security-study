# 01 basics (Spring Boot 2.7 / Spring Security 5.8)

「2.7 時点の書き方」を手を動かして確認するモジュール。
**同じ振る舞いを 2 通りの書き方で実装**してあるので、読み比べるのが主目的です。

| プロファイル | 設定クラス | 書き方 |
|---|---|---|
| `adapter` | [`LegacyAdapterSecurityConfig`](src/main/java/com/example/basics27/config/LegacyAdapterSecurityConfig.java) | `WebSecurityConfigurerAdapter` を継承（5.7 で非推奨 / 6.0 で削除） |
| デフォルト | [`ComponentSecurityConfig`](src/main/java/com/example/basics27/config/ComponentSecurityConfig.java) | `SecurityFilterChain` を Bean 化（5.7 以降の推奨。3.x もこの形） |

## 動かす

```bash
# 推奨の書き方で起動
./mvnw spring-boot:run

# 旧来の WebSecurityConfigurerAdapter 方式で起動
./mvnw spring-boot:run -Dspring-boot.run.profiles=adapter

# 認可ルールのテスト（両方の書き方で同じ結果になることを確認する）
./mvnw test
```

http://localhost:8080 を開く。ログインできるユーザー:

| ユーザー | パスワード | 権限 |
|---|---|---|
| `user` | `password` | `ROLE_USER` |
| `admin` | `password` | `ROLE_USER`, `ROLE_ADMIN` |

## 試すこと

```bash
# 未認証 → 401（/api/** は AuthenticationEntryPoint を Basic にしてあるため）
curl -i localhost:8080/api/me

# 認証あり → 200
curl -u user:password localhost:8080/api/me

# 権限不足 → 403（401 ではない！ここの区別が重要）
curl -u user:password -i localhost:8080/api/admin/stats

# 管理者 → 200
curl -u admin:password localhost:8080/api/admin/stats
```

ブラウザでは次を確認する。

1. `user` でログイン → `/admin/dashboard` にアクセス → 403 画面（**URL ベースの認可**）
2. `user` でログイン → `/report/admin-only` にアクセス → 403 画面
   （URL は誰でも OK だが、呼び出し先の `@PreAuthorize` が弾く = **メソッドセキュリティ**）
3. `admin` でログイン → どちらも 200

## 学びポイント

### 1. この 2 つの書き方の差分が、そのまま 3.x 移行の作業リストになる

```java
// 旧: メソッドチェーン + and()
http.authorizeRequests()
        .antMatchers("/admin/**").hasRole("ADMIN")
        .and()
    .formLogin().loginPage("/login");

// 新: ラムダ DSL（and() が要らない）
http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/admin/**").hasRole("ADMIN"))   // 5.8 では antMatchers
    .formLogin(form -> form.loginPage("/login"));
```

詳しい対応表は [`../docs/03-migration-checklist.md`](../docs/03-migration-checklist.md)。

### 2. 「デフォルト拒否」を必ず書く

`anyRequest().authenticated()` を最後に置くのが基本。
ルールは**上から順に評価され、最初にマッチしたものだけ**が使われる。
`permitAll()` を広いパスで先に書いてしまうと、後ろの `hasRole(...)` が死ぬ。

### 3. formLogin と httpBasic を併用すると「入口」が競合する

未認証アクセスをどう扱うかは `AuthenticationEntryPoint` が決める。
両方設定すると後に書いたほうが全 URL のデフォルトになるため、
画面アクセスでもブラウザの Basic 認証ダイアログが出てしまうことがある。
このサンプルでは `defaultAuthenticationEntryPointFor(...)` で
`/api/**` は 401、それ以外は `/login` へリダイレクト、と**明示**している。

### 4. 401 と 403

| ステータス | 意味 | 担当 |
|---|---|---|
| 401 Unauthorized | 誰だか分からない（未認証） | `AuthenticationEntryPoint` |
| 403 Forbidden | 誰かは分かるが権限がない（認可 NG） | `AccessDeniedHandler` |

### 5. `@EnableGlobalMethodSecurity` と `@EnableMethodSecurity`

- 2.7 でよく見るのは `@EnableGlobalMethodSecurity(prePostEnabled = true)`（現在は非推奨）
- 5.8 以降は `@EnableMethodSecurity`（`prePostEnabled` は既定で true、内部実装が `AuthorizationManager` ベース）
