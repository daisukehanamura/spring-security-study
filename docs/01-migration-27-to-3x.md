# 2.7 の書き方 → 3.x の書き方（移行チェックリスト）

対応するサンプル: [`01-basics-boot27`](../01-basics-boot27) と [`02-basics-boot3`](../02-basics-boot3)
（同じ振る舞いを両方の書き方で実装してあります）

## バージョン対応

| Spring Boot | Spring Security | Java | 備考 |
|---|---|---|---|
| 2.7.x | 5.8.x | 8+ | `WebSecurityConfigurerAdapter` が**非推奨**になった版 |
| 3.0–3.2 | 6.0–6.2 | 17+ | Adapter **削除**、`javax` → `jakarta` |
| 3.3–3.5 | 6.3–6.5 | 17+ | `RoleHierarchy` Bean の自動適用など改善 |
| 4.x | 7.x | 17+ | 非ラムダ DSL の削除など。3.x を理解してから |

## API 対応表

| 2.7 (Security 5.8) | 3.x (Security 6.x) | メモ |
|---|---|---|
| `extends WebSecurityConfigurerAdapter` | `@Bean SecurityFilterChain` | **5.7 で非推奨 / 6.0 で削除** |
| `authorizeRequests()` | `authorizeHttpRequests()` | 内部が `AuthorizationManager` ベースに |
| `antMatchers("/a/**")` | `requestMatchers("/a/**")` | `mvcMatchers` / `regexMatchers` も `requestMatchers` に統合 |
| `http.csrf().disable()` | `http.csrf(csrf -> csrf.disable())` | 6.1 でチェーン形式が非推奨、7.0 で削除 |
| `.and()` で連結 | ラムダ DSL | `and()` は使わない書き方へ |
| `configure(WebSecurity)` + `ignoring()` | `WebSecurityCustomizer` Bean | できれば `permitAll()` を使う |
| `@EnableGlobalMethodSecurity(prePostEnabled=true)` | `@EnableMethodSecurity` | 後者は `prePostEnabled` が既定 true |
| `AccessDecisionManager` / `Voter` | `AuthorizationManager` | 独自 Voter を書いていたら要移行 |
| `FilterSecurityInterceptor` | `AuthorizationFilter` | ログやカスタムフィルタの挿入位置に影響 |
| `WebSecurityConfigurerAdapter#authenticationManagerBean()` | `AuthenticationManager` を Bean 定義 | `ProviderManager` を自分で組む |
| `NoOpPasswordEncoder`（テストで使いがち） | 使わない | `PasswordEncoderFactories` を使う |
| `thymeleaf-extras-springsecurity5` | `thymeleaf-extras-springsecurity6` | **artifactId が違う**ので差し替え忘れ注意 |
| `javax.servlet.*` | `jakarta.servlet.*` | 自前フィルタを書いていたら全部書き換え |

## 移行手順（おすすめの順番）

1. **2.7 のままコンポーネント方式へ書き換える**
   `WebSecurityConfigurerAdapter` を捨てて `SecurityFilterChain` Bean にする。
   ここまでは 2.7 でも動くので、**テストが通ることを確認してからコミット**できる。
2. `authorizeRequests` → `authorizeHttpRequests` に変える（2.7 でも可）
3. ラムダ DSL に統一する（2.7 でも可）
4. **ここで初めて Boot 3.x に上げる**
   - `antMatchers` → `requestMatchers`
   - `javax` → `jakarta`
   - Thymeleaf の extras を 6 系へ
5. `@EnableGlobalMethodSecurity` → `@EnableMethodSecurity`
6. 認可ルールの回帰テストを流す（**ここが一番大事**）

> 1〜3 を 2.7 のうちに済ませておくと、Boot 3 へのジャンプが「依存関係とパッケージ名の話」だけになります。
> 逆に全部まとめてやると、落ちたときに原因の切り分けができなくなります。

## 認可ルールのテストを先に書く

移行で怖いのは「うっかり緩くなること」。先に下のような形で**現状の仕様をテストに固定**してから移行します。

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

    @Autowired MockMvc mockMvc;

    @Test @WithMockUser(roles = "USER")
    void userCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/whoami"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrlPattern("**/login"));
    }
}
```

実例: [`01-basics-boot27` のテスト](../01-basics-boot27/src/test/java/com/example/basics27/AbstractSecurityRulesTest.java)
は 1 つのテストクラスを 2 プロファイル（旧書き方 / 新書き方）で継承して動かし、
「書き方を変えても振る舞いが変わらない」ことを担保しています。

## よく踏む落とし穴

- **ルールの評価順**: 上から順、最初にマッチした 1 つだけ。広いパスを先に書くと後ろが死ぬ
- **`anyRequest()` の書き忘れ**: 設定漏れの URL が素通りになる。必ず最後に `authenticated()` か `denyAll()`
- **`hasRole("ROLE_ADMIN")`**: `ROLE_` は `hasRole` が自動で付けるので `ROLE_ROLE_ADMIN` になる
- **`securityMatcher` なしのチェーンを `@Order(1)`**: 後続チェーンが全部無効（起動はするので気づかない）
- **`formLogin` と `httpBasic` の併用**: 未認証時の入口（`AuthenticationEntryPoint`）が競合する。
  URL ごとに `defaultAuthenticationEntryPointFor(...)` で明示するのが安全
