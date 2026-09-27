package com.example.basics27.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * ＝＝＝ 2.7 でも使える「新しい」書き方（コンポーネントベース）＝＝＝
 *
 * WebSecurityConfigurerAdapter を継承せず、{@link SecurityFilterChain} を Bean として公開する。
 * この形にしておけば、Boot 3.x への移行は
 *   authorizeRequests -> authorizeHttpRequests
 *   antMatchers       -> requestMatchers
 * の置き換えだけで済む（＝移行コストが小さい）。
 *
 * 起動: ./mvnw spring-boot:run   （デフォルトプロファイル）
 */
@Profile("!adapter")
@Configuration
@EnableWebSecurity
// 5.8 以降の推奨。AuthorizationManager ベースで実装されており、
// prePostEnabled=true が既定なので @PreAuthorize がそのまま使える。
@EnableMethodSecurity
public class ComponentSecurityConfig {

    /**
     * 認可ルールとログイン方式。
     * 5.8 では authorizeHttpRequests() が使えるが、まだ antMatchers() も生きている。
     * （requestMatchers(String...) が使えるのは Security 6.x = Boot 3.x から）
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .antMatchers("/", "/login", "/css/**").permitAll()
                .antMatchers("/admin/**").hasRole("ADMIN")
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/whoami", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout")
            )
            .httpBasic(basic -> {})
            .csrf(csrf -> csrf.ignoringAntMatchers("/api/**"))
            .exceptionHandling(ex -> ex
                // 未認証アクセスの「入口」(AuthenticationEntryPoint)を URL で切り替える。
                //   /api/**  -> 401 + WWW-Authenticate（curl / SPA 向け）
                //   それ以外 -> /login へリダイレクト（ブラウザ向け）
                //
                // ★ハマりどころ: formLogin() と httpBasic() を併用すると、
                //   後から設定されたほうの入口が全 URL のデフォルトになってしまう。
                //   （httpBasic を後に書くと、画面アクセスでもブラウザの Basic 認証ダイアログが出る）
                //   なので「どの URL でどの入口を使うか」を明示するのが確実。
                //   マッピングは登録順に評価されるので、限定的な条件を先に書く。
                .defaultAuthenticationEntryPointFor(
                        basicAuthenticationEntryPoint(),
                        new AntPathRequestMatcher("/api/**"))
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        AnyRequestMatcher.INSTANCE)
                .accessDeniedPage("/403")
            );

        return http.build();
    }

    private BasicAuthenticationEntryPoint basicAuthenticationEntryPoint() {
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("api");
        return entryPoint;
    }

    /**
     * WebSecurity#ignoring() の置き換え。
     * なお「認証不要にしたいだけ」なら permitAll() を使うほうが安全
     * （ignoring するとセキュリティヘッダ等も付かなくなる）。
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().antMatchers("/favicon.ico", "/images/**");
    }
}
