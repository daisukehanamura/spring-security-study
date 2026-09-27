package com.example.basics27.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * ＝＝＝ 旧来（Spring Security 5.6 以前で標準だった）書き方 ＝＝＝
 *
 * WebSecurityConfigurerAdapter を継承し、configure(...) をオーバーライドして設定する方式。
 * Spring Security 5.7（Boot 2.7）で <b>非推奨</b> になり、6.0（Boot 3.0）で <b>削除</b> された。
 *
 * つまり 2.7 のプロジェクトでこの書き方が残っていたら、3.x へ上げる前に
 * {@link ComponentSecurityConfig} の形へ書き換える必要がある。
 *
 * 起動: ./mvnw spring-boot:run -Dspring-boot.run.profiles=adapter
 */
@Profile("adapter")
@Configuration
@EnableWebSecurity
// 2.7 時代によく見る書き方。prePostEnabled=true で @PreAuthorize / @PostAuthorize が有効になる。
// （5.8 以降は @EnableMethodSecurity が推奨。ComponentSecurityConfig 側を参照）
@EnableGlobalMethodSecurity(prePostEnabled = true)
@SuppressWarnings("deprecation") // 学習用に「あえて」非推奨 API を使っている
public class LegacyAdapterSecurityConfig extends WebSecurityConfigurerAdapter {

    /**
     * 認可ルール・ログイン方式などの HTTP レベルの設定。
     * ポイント: メソッドチェーン（and() でつなぐ）と antMatchers / authorizeRequests。
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            // ---- 認可ルール ----
            // authorizeRequests() は 5.8 で非推奨、6.x で削除 -> authorizeHttpRequests() へ
            .authorizeRequests()
                // antMatchers() は 6.x で削除 -> requestMatchers() へ
                .antMatchers("/", "/login", "/css/**").permitAll()
                .antMatchers("/admin/**").hasRole("ADMIN")   // ROLE_ADMIN を要求（"ROLE_" は自動で付く）
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/**").authenticated()
                .anyRequest().authenticated()                // 書いていない URL はログイン必須（＝デフォルト拒否）
                .and()
            // ---- フォームログイン ----
            .formLogin()
                .loginPage("/login")
                .loginProcessingUrl("/login")   // POST 先。Security がここを横取りして認証する
                .defaultSuccessUrl("/whoami", true)
                .failureUrl("/login?error")
                .permitAll()
                .and()
            .logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout")
                .and()
            // ---- API 用に HTTP Basic も許可（ブラウザ以外からの確認用）----
            .httpBasic()
                .and()
            // ---- CSRF ----
            // ブラウザのフォームには CSRF 対策を効かせたいので有効のまま。
            // ただし curl から叩く API では邪魔なので除外する。
            .csrf()
                .ignoringAntMatchers("/api/**")
                .and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
            .exceptionHandling()
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
                .accessDeniedPage("/403");
    }

    private BasicAuthenticationEntryPoint basicAuthenticationEntryPoint() {
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("api");
        return entryPoint;
    }

    /**
     * 静的リソースなど「Security のフィルタを通さない」パスの指定。
     * 3.x では WebSecurityCustomizer Bean で同じことをする（ComponentSecurityConfig 参照）。
     */
    @Override
    public void configure(WebSecurity web) {
        web.ignoring().antMatchers("/favicon.ico", "/images/**");
    }
}
