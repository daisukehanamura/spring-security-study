# 認可（ROLE による制御）の設計

対応するサンプル: [`02-basics-boot3`](../02-basics-boot3)

## ROLE と authority は同じ仕組み

Spring Security の内部にあるのは `GrantedAuthority`（ただの文字列）だけです。
ROLE はその文字列に `ROLE_` という**慣習的なプレフィックス**を付けただけのもの。

| 書き方 | 実際に比較される文字列 |
|---|---|
| `hasRole("ADMIN")` | `ROLE_ADMIN` |
| `hasAnyRole("ADMIN","MANAGER")` | `ROLE_ADMIN` or `ROLE_MANAGER` |
| `hasAuthority("ROLE_ADMIN")` | `ROLE_ADMIN` |
| `hasAuthority("report:read")` | `report:read` |
| `hasRole("report:read")` | `ROLE_report:read` ← 事故 |

`User.withUsername("x").roles("ADMIN")` は `ROLE_ADMIN` を、
`.authorities("report:read")` はそのままの文字列を登録します。
（`roles(...)` と `authorities(...)` を両方呼ぶと**後の呼び出しが前を上書きする**ので、
併用したいときは `.authorities("ROLE_MANAGER", "report:read")` のように 1 回で書く）

## 設計の指針

```
ROLE     = 立場・職務        例: ROLE_ADMIN, ROLE_MANAGER, ROLE_USER
authority = できる操作(権限)   例: order:approve, report:read, user:delete
```

- 画面や API が増えると、`hasRole("ADMIN")` の直接判定はすぐ破綻する
  （「経理だけ見られる画面」「一部の管理者だけ許す操作」が必ず出てくる）
- **URL やメソッドには「操作(authority)」で条件を書き、ROLE には操作を束ねる役割を持たせる**
  のが長持ちする設計。`ROLE_MANAGER = {order:approve, report:read}` のようにマッピングを DB に持つ
- 認証方式（フォーム / SAML / OAuth2）が何であっても、最終的に authority に落とせば
  認可ルールは 1 か所に集約できる

## ROLE 階層

```java
@Bean
RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix()
            .role("ADMIN").implies("MANAGER")
            .role("MANAGER").implies("USER")
            .build();
}
```

- `ADMIN` は `MANAGER` / `USER` 用の URL・メソッドにそのまま通る
- Security **6.3 以降は URL 認可とメソッドセキュリティの両方に自動適用**
  （6.2 以前は `MethodSecurityExpressionHandler` に手で渡す必要があった）
- `Authentication#getAuthorities()` の一覧には展開結果は**現れない**（判定時に展開される）

## URL ベースの認可

```java
http.authorizeHttpRequests(authorize -> authorize
    .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
    .requestMatchers("/", "/login").permitAll()
    .requestMatchers(HttpMethod.GET,  "/api/orders/**").hasAnyRole("USER", "ADMIN")
    .requestMatchers(HttpMethod.POST, "/api/orders/**").hasRole("ADMIN")
    .requestMatchers("/reports/export/**").access(AuthorizationManagers.allOf(   // 複合条件
            AuthorityAuthorizationManager.hasAuthority("report:read"),
            AuthorityAuthorizationManager.hasRole("MANAGER")))
    .requestMatchers("/reports/**").hasAuthority("report:read")
    .anyRequest().authenticated()      // ★デフォルト拒否を最後に必ず
);
```

ルールは**上から順に評価され、最初にマッチした 1 つだけ**が使われます。
狭い条件を先に、広い条件を後に。`/reports/**` を先に書くと `/reports/export/**` の
厳しい条件は永遠に評価されません（実際にこのサンプルで踏んでテストで気づいた罠）。

## メソッドベースの認可

| アノテーション | 評価 | 用途 |
|---|---|---|
| `@PreAuthorize("hasRole('ADMIN')")` | 実行前 | 基本これ。ROLE / 引数チェック |
| `@PreAuthorize("#owner == authentication.name")` | 実行前 | 引数と認証情報の突き合わせ |
| `@PostAuthorize("returnObject.owner == authentication.name")` | 実行後 | 戻り値の所有者チェック |
| `@PostFilter("filterObject.owner == authentication.name")` | 実行後 | 一覧から見せてよい要素だけ残す |
| `@PreFilter` | 実行前 | 引数のコレクションを絞る |

注意点:

- `@PostAuthorize` / `@PostFilter` は**一度実行してから捨てる**ので更新系には使わない
- `@PostFilter` は全件取得後のフィルタなので、件数が多いと非効率。実務では SQL の WHERE に落とす
- アノテーションは **public メソッド + プロキシ経由の呼び出し**でのみ効く。
  同一クラス内の `this.method()` 呼び出しには効かない（AOP の基本と同じ）
- 未認証で到達すると `AccessDeniedException` ではなく
  `AuthenticationCredentialsNotFoundException`（`AuthenticationException` 系）が飛ぶ

## 401 と 403

| ステータス | 意味 | 担当コンポーネント |
|---|---|---|
| 401 Unauthorized | 誰だか分からない（未認証） | `AuthenticationEntryPoint` |
| 403 Forbidden | 誰かは分かるが権限が足りない | `AccessDeniedHandler` |

API と画面が同居するアプリでは、`/api/**` は 401 を返し、画面はログイン画面へ
リダイレクトしたいことが多い。その場合は URL ごとに入口を明示します。

```java
http.exceptionHandling(ex -> ex
    .defaultAuthenticationEntryPointFor(basicEntryPoint, new AntPathRequestMatcher("/api/**"))
    .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), AnyRequestMatcher.INSTANCE)
    .accessDeniedPage("/403"));
```

## 画面側の出し分けは「おまけ」

```html
<div sec:authorize="hasRole('ADMIN')">管理メニュー</div>
```

これは UI の見栄えのためであり、**セキュリティではありません**。
必ずサーバ側（URL 認可 / メソッドセキュリティ）とセットで書くこと。

## テストで固定する

```java
@Test @WithMockUser(roles = "USER")
void userCannotAccessAdmin() throws Exception {
    mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
}

@Test @WithMockUser(authorities = "report:read")
void authorityBasedRule() throws Exception {
    mockMvc.perform(get("/reports/list")).andExpect(status().isOk());
}
```

`@WithMockUser(roles = "X")` は `ROLE_X` を、`@WithMockUser(authorities = "x")` は
`x` をそのまま与えます（ここも ROLE_ の有無でハマりやすい）。
実例は [`WebAuthorizationTest`](../02-basics-boot3/src/test/java/com/example/basics3/WebAuthorizationTest.java)。
