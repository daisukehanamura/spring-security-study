# 02 basics (Spring Boot 3.5 / Spring Security 6.5)

01 と同じ題材を **3.x の書き方**で実装し、さらに **認可（ROLE 制御）** を掘り下げたモジュール。

- [`SecurityConfig`](src/main/java/com/example/basics3/config/SecurityConfig.java) … フィルタチェーン 2 本 + 認可ルール + ROLE 階層
- [`OrderService`](src/main/java/com/example/basics3/service/OrderService.java) … メソッドセキュリティ 4 種類
- [`MethodSecurityConfig`](src/main/java/com/example/basics3/config/MethodSecurityConfig.java) … 式評価のカスタマイズ差し込み口

## 動かす

```bash
./mvnw spring-boot:run      # http://localhost:8081
./mvnw test                 # 認可ルールをテストで確認（20 件）
```

| ユーザー | パスワード | 持っている権限 | 階層適用後に通る ROLE |
|---|---|---|---|
| `user` | `password` | `ROLE_USER` | USER |
| `manager` | `password` | `ROLE_MANAGER`, `report:read` | MANAGER, USER |
| `admin` | `password` | `ROLE_ADMIN`, `report:read` | ADMIN, MANAGER, USER |

```bash
curl localhost:8081/api/public/ping                        # 認証不要
curl -u user:password localhost:8081/api/orders            # 自分の注文だけ返る(@PostFilter)
curl -u admin:password localhost:8081/api/orders           # 全件返る
curl -u user:password -i localhost:8081/api/orders/4       # 他人の注文 -> 403(@PostAuthorize)
curl -u user:password -i -X POST localhost:8081/api/orders # POST は ADMIN のみ -> 403
curl -i localhost:8081/api/me                              # 未認証 -> 401
```

## 学びポイント

### 1. フィルタチェーンを用途ごとに分ける

```java
@Bean @Order(1)
SecurityFilterChain apiFilterChain(HttpSecurity http) {
    http.securityMatcher("/api/**")   // ★このチェーンの担当範囲
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .httpBasic(...).csrf(csrf -> csrf.disable());
}

@Bean @Order(2)
SecurityFilterChain webFilterChain(HttpSecurity http) { ... }  // 残り全部
```

- `@Order` の小さい順に評価され、**最初にマッチしたチェーン 1 本だけ**が動く
- `/api/**` のリクエストは web 側のチェーンを**通らない**（＝フォームログインの設定も効かない）
- `securityMatcher` を書き忘れたチェーンは「全 URL 担当」になるので、
  `@Order(1)` に置くと後続のチェーンが全部死ぬ（起動はするので気づきにくい）

### 2. 認可ルールは「上から順、最初にマッチしたものだけ」

このサンプルには実際にその罠を踏んだ跡があります（テストで発見）。

```java
// ✗ これだと export の厳しい条件が絶対に評価されない
.requestMatchers("/reports/**").hasAuthority("report:read")
.requestMatchers("/reports/export/**").access(allOf(...))

// ○ 狭い条件を先に書く
.requestMatchers("/reports/export/**").access(allOf(...))
.requestMatchers("/reports/**").hasAuthority("report:read")
```

### 3. `hasRole` と `hasAuthority`

| 書き方 | 実際に比較される文字列 |
|---|---|
| `hasRole("ADMIN")` | `ROLE_ADMIN` |
| `hasAuthority("ROLE_ADMIN")` | `ROLE_ADMIN` |
| `hasAuthority("report:read")` | `report:read` |
| `hasRole("report:read")` | `ROLE_report:read` ← 事故 |

**使い分けの考え方**: ROLE は「職務・立場」（ADMIN / MANAGER）、authority は「できる操作」（`report:read`, `order:approve`）。
画面や URL が増えるほど、ROLE 直接判定より authority（権限）判定のほうが破綻しにくい。

### 4. ROLE 階層

```java
@Bean
RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix()
            .role("ADMIN").implies("MANAGER")
            .role("MANAGER").implies("USER")
            .build();
}
```

- `ADMIN` は `MANAGER` / `USER` 用の URL・メソッドにもそのまま通る
- Security **6.3 以降は URL 認可とメソッドセキュリティの両方に自動適用**される
  （6.2 以前は `MethodSecurityExpressionHandler` への明示的な設定が必要だった）
- `Authentication#getAuthorities()` の一覧には展開結果は出ない（判定時に展開される）ので、
  画面に権限一覧を出しているとギャップに驚くことがある

### 5. メソッドセキュリティの 4 種類

| アノテーション | 評価タイミング | 典型用途 |
|---|---|---|
| `@PreAuthorize` | 実行前 | ROLE / 引数チェック（まずこれ） |
| `@PostAuthorize` | 実行後 | 戻り値の所有者チェック |
| `@PostFilter` | 実行後 | 一覧から見せてよい要素だけ残す |
| `@PreFilter` | 実行前 | 引数のコレクションを絞る |

- `@PostAuthorize` / `@PostFilter` は**一度実行してから捨てる**ので、更新系には使わない
- `@PostFilter` は全件取得してからフィルタするので、件数が多いと非効率。
  実務では SQL の WHERE 句に落とすのが基本（学習用として理解したうえで使う）
- 未認証で到達すると `AccessDeniedException` ではなく
  `AuthenticationCredentialsNotFoundException` が飛ぶ（テストに書いてあります）

### 6. 2.7 から名前が変わった API（このモジュールで使っているもの）

| 2.7 (Security 5.8) | 3.x (Security 6.x) |
|---|---|
| `WebSecurityConfigurerAdapter` | 削除（`SecurityFilterChain` Bean のみ） |
| `authorizeRequests()` | `authorizeHttpRequests()` |
| `antMatchers()` / `mvcMatchers()` | `requestMatchers()` |
| `http.csrf().disable()` | `http.csrf(csrf -> csrf.disable())`（6.1 でチェーン形式は非推奨） |
| `FilterSecurityInterceptor` | `AuthorizationFilter` |
| `WebSecurity#ignoring()` | `WebSecurityCustomizer`（できれば `permitAll()` を使う） |
