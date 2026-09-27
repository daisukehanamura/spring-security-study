package com.example.basics3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Spring Security 6.x（Boot 3.x）の設定。
 *
 * <h2>2.7 からの主な変更点</h2>
 * <ul>
 *   <li>{@code WebSecurityConfigurerAdapter} は削除 → {@link SecurityFilterChain} Bean のみ</li>
 *   <li>{@code authorizeRequests()} 削除 → {@code authorizeHttpRequests()}</li>
 *   <li>{@code antMatchers()} / {@code mvcMatchers()} 削除 → {@code requestMatchers()} に統合</li>
 *   <li>6.1 以降、引数なしの {@code http.csrf()} 形式（メソッドチェーン）は非推奨 → ラムダ DSL 必須</li>
 *   <li>認可の実行主体が {@code FilterSecurityInterceptor} → {@code AuthorizationFilter} に変更</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
// prePostEnabled は既定 true。@PreAuthorize / @PostAuthorize / @PreFilter / @PostFilter が使える。
// @Secured や JSR-250(@RolesAllowed) を使いたい場合は securedEnabled / jsr250Enabled を true にする。
@EnableMethodSecurity
public class SecurityConfig {

    // ------------------------------------------------------------------
    // 1) API 用のフィルタチェーン（/api/** だけを担当）
    // ------------------------------------------------------------------

    /**
     * 「API はステートレス + Basic 認証、画面はセッション + フォームログイン」のように
     * 性質の違う経路は<b>フィルタチェーンを分ける</b>のが 3.x 流のきれいな書き方。
     *
     * ポイント:
     * <ul>
     *   <li>{@code securityMatcher(...)} でこのチェーンが担当する URL を決める</li>
     *   <li>{@code @Order} が小さいチェーンから順に評価され、<b>最初にマッチした 1 本だけ</b>が使われる</li>
     *   <li>つまり /api/** のリクエストは 2) の webFilterChain を一切通らない</li>
     * </ul>
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/public/**").permitAll()
                // HTTP メソッド単位の指定。参照は USER でも、更新は ADMIN のみ、という典型パターン。
                .requestMatchers(HttpMethod.GET, "/api/orders/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/orders/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> basic.authenticationEntryPoint(apiEntryPoint()))
            // セッションを作らない。Cookie を使わないので CSRF 対策も不要になる。
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    // ------------------------------------------------------------------
    // 2) 画面用のフィルタチェーン（上で拾われなかった全リクエスト）
    // ------------------------------------------------------------------

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                // 静的リソース用の専用マッチャ（/css/** などを自分で列挙しなくてよい）
                .requestMatchers(org.springframework.boot.autoconfigure.security.servlet.PathRequest
                        .toStaticResources().atCommonLocations()).permitAll()
                .requestMatchers("/", "/login", "/error").permitAll()
                // hasRole("ADMIN") = ROLE_ADMIN を持っているか（ROLE_ は自動で付く）
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // ROLE 階層(roleHierarchy Bean)により、ADMIN は MANAGER を包含する
                .requestMatchers("/manager/**").hasRole("MANAGER")
                // ★ルールは上から順に評価され、最初にマッチした 1 つだけが使われる。
                //   そのため「狭い条件を先に、広い条件を後に」書く。
                //   もし /reports/** を先に書くと、下の /reports/export/** の厳しい条件は
                //   永遠に評価されない（＝意図せず緩くなる）。よくある事故。
                //
                // 複合条件。allOf / anyOf で AuthorizationManager を組み合わせられる（式文字列を使わない書き方）
                .requestMatchers("/reports/export/**").access(AuthorizationManagers.allOf(
                        AuthorityAuthorizationManager.hasAuthority("report:read"),
                        AuthorityAuthorizationManager.hasRole("MANAGER")))
                // ROLE ではなく「権限(authority)」で判定する例。ROLE_ プレフィックスは付かない。
                .requestMatchers("/reports/**").hasAuthority("report:read")
                // ★最後は必ずデフォルト拒否。書き忘れると「設定漏れの URL が素通り」になる。
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                // 元々アクセスしようとしていた URL に戻す（RequestCache の既定動作）。
                // 常に固定の URL へ飛ばしたいなら defaultSuccessUrl("/whoami", true)
                .defaultSuccessUrl("/whoami")
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/?logout")
                // ログアウトは POST が既定（CSRF 対策のため）。GET にしたい場合は logoutRequestMatcher を指定する。
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .exceptionHandling(ex -> ex.accessDeniedPage("/403"))
            .sessionManagement(session -> session
                // セッション固定化攻撃対策（既定で有効。ログイン時にセッション ID を変える）
                .sessionFixation(sessionFixation -> sessionFixation.changeSessionId())
                // 同時ログイン数の制限。2 つ目のログインで古い方を無効化する設定例。
                .maximumSessions(1).maxSessionsPreventsLogin(false)
            )
            .requestCache(cache -> cache.requestCache(new HttpSessionRequestCache()));
        return http.build();
    }

    // ------------------------------------------------------------------
    // 3) 認可のための共通 Bean
    // ------------------------------------------------------------------

    /**
     * ROLE 階層。ADMIN は MANAGER の権限を、MANAGER は USER の権限を自動的に含む。
     *
     * これがないと「管理者なのに一般ユーザー向け画面が 403」という残念な事故が起きるので、
     * ROLE 設計をするときは階層をセットで考える。
     *
     * Security 6.3 以降は、この Bean が URL 認可にもメソッドセキュリティにも自動で使われる。
     * 6.2 以前（および Boot 2.7 = 5.8）では、メソッドセキュリティ側には自動で効かないので
     * {@link MethodSecurityConfig} のように ExpressionHandler へ明示的に渡す必要があった。
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("MANAGER")
                .role("MANAGER").implies("USER")
                .build();
    }

    /** 学習用のユーザー定義（実務では DB から引く UserDetailsService に差し替える）。 */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = User.withUsername("user")
                .password(passwordEncoder.encode("password"))
                .roles("USER")
                .build();
        UserDetails manager = User.withUsername("manager")
                .password(passwordEncoder.encode("password"))
                .roles("MANAGER")                     // 階層により USER も含む
                .authorities("ROLE_MANAGER", "report:read")  // ROLE と細かい権限は併用できる
                .build();
        UserDetails admin = User.withUsername("admin")
                .password(passwordEncoder.encode("password"))
                .authorities("ROLE_ADMIN", "report:read")
                .build();
        return new InMemoryUserDetailsManager(user, manager, admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private BasicAuthenticationEntryPoint apiEntryPoint() {
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("api");
        return entryPoint;
    }

}
