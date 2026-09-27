# Spring Security のアーキテクチャ（最初にここだけ押さえる）

Spring Security は「サーブレットフィルタの束」です。ここが分かると、
設定でハマったときに「どのフィルタの話をしているのか」で切り分けられるようになります。

## 全体像

```
ブラウザ / curl
   |
   v
[ Servlet Container (Tomcat) ]
   |
   v
DelegatingFilterProxy  ...... Spring の Bean にフィルタ処理を委譲する橋渡し
   |
   v
FilterChainProxy ("springSecurityFilterChain")
   |
   |-- SecurityFilterChain #1  (securityMatcher: /api/**)   ← 最初にマッチした 1 本だけ実行
   |-- SecurityFilterChain #2  (securityMatcher: 指定なし = 全部)
   |
   v
DispatcherServlet -> @Controller
```

`SecurityFilterChain` 1 本の中身（順番が重要・抜粋）:

| 順 | フィルタ | 役割 |
|---|---|---|
| 1 | `DisableEncodeUrlFilter` / `WebAsyncManagerIntegrationFilter` | 下準備 |
| 2 | `SecurityContextHolderFilter` | セッションから `SecurityContext` を復元する |
| 3 | `HeaderWriterFilter` | `X-Frame-Options` などのセキュリティヘッダ |
| 4 | `CsrfFilter` | CSRF トークン検証 |
| 5 | `LogoutFilter` | `/logout` の処理 |
| 6 | `UsernamePasswordAuthenticationFilter` | **フォームログインの認証**（`/login` の POST） |
| 6' | `Saml2WebSsoAuthenticationFilter` | **SAML の認証**（`/login/saml2/sso/{id}`） |
| 6'' | `OAuth2LoginAuthenticationFilter` | **OAuth2/OIDC ログインの認証** |
| 7 | `BasicAuthenticationFilter` | HTTP Basic の認証 |
| 8 | `RequestCacheAwareFilter` | ログイン前に見ようとした URL に戻す |
| 9 | `AnonymousAuthenticationFilter` | 未認証を「匿名ユーザー」として扱う |
| 10 | `ExceptionTranslationFilter` | 認証/認可の例外を 401 / 403 / リダイレクトに変換 |
| 11 | `AuthorizationFilter` | **認可の判定**（2.7 では `FilterSecurityInterceptor`） |

> 起動時に実際の並び順を見たいとき:
> `logging.level.org.springframework.security.web.FilterChainProxy=DEBUG`

## 認証（Authentication）の流れ

```
[認証用フィルタ]                     例: UsernamePasswordAuthenticationFilter
   | 未認証の Authentication を組み立てる（username/password を詰める）
   v
AuthenticationManager (ProviderManager)
   | 複数の AuthenticationProvider を順に試す
   v
DaoAuthenticationProvider
   |-- UserDetailsService#loadUserByUsername  ...... ユーザーを引く（DB / LDAP / メモリ）
   |-- PasswordEncoder#matches                ...... パスワード照合
   v
認証済み Authentication（principal + authorities）
   v
SecurityContextHolder に格納 + セッションへ保存（＝以降のリクエストは 2 のフィルタで復元）
```

登場人物を 1 行で:

| 型 | 役割 |
|---|---|
| `Authentication` | 「誰が」「どんな権限で」入っているか。principal + authorities |
| `GrantedAuthority` | 権限 1 つ。`ROLE_ADMIN` や `report:read` のような文字列 |
| `SecurityContext` | `Authentication` の入れ物 |
| `SecurityContextHolder` | `SecurityContext` を ThreadLocal で持つ入口 |
| `UserDetails` / `UserDetailsService` | ユーザー情報とその取得方法 |
| `AuthenticationProvider` | 認証方式ごとの実装（DB 照合、SAML、JWT…） |
| `AuthenticationEntryPoint` | **未認証**のときに何をするか（401 / ログイン画面へ） |
| `AccessDeniedHandler` | **権限不足**のときに何をするか（403 画面など） |
| `AuthorizationManager` | 認可の判定ロジック（3.x の中心。2.7 は `AccessDecisionManager`） |

## 認可（Authorization）の 2 レイヤ

1. **URL ベース**: `authorizeHttpRequests { ... }` → `AuthorizationFilter` が判定
2. **メソッドベース**: `@PreAuthorize` など → AOP のプロキシが判定

URL だけで守ると「別の経路から同じサービスを呼ばれたら素通り」になるので、
重要な処理はサービス層でも守る（多層防御）。

## 認証方式が増えても認可は共通

フォームログインでも SAML でも OAuth2 でも、最終的に得られるのは `Authentication` と
`GrantedAuthority` の集合です。だから **認可ルール（誰が何にアクセスできるか）は
認証方式に依存しません**。

```
フォームログイン --\
SAML SSO ----------> Authentication (authorities: ROLE_ADMIN, ...) --> 同一の認可ルール
OAuth2/OIDC ------/
```

これが 03（フォーム + SAML 共存）と 04（OAuth2）を理解するときの土台になります。
