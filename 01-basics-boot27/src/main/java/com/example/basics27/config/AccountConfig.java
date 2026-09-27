package com.example.basics27.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * 認証に使うユーザー情報とパスワードエンコーダ。
 * ここは 2.7 でも 3.x でも書き方がほぼ変わらない部分（＝差分を理解するときに気にしなくてよい部分）。
 */
@Configuration
public class AccountConfig {

    /**
     * 学習用にメモリ上のユーザーを定義する。
     * 実務では JdbcUserDetailsManager や独自の UserDetailsService 実装に置き換える。
     *
     * roles("USER") と書くと、権限は "ROLE_USER" として登録される（ROLE_ プレフィックスが自動で付く）。
     * authorities("report:read") のように書くと、プレフィックスなしの権限として登録される。
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = User.withUsername("user")
                .password(passwordEncoder.encode("password"))
                .roles("USER")                       // -> ROLE_USER
                .build();

        UserDetails admin = User.withUsername("admin")
                .password(passwordEncoder.encode("password"))
                .roles("USER", "ADMIN")              // -> ROLE_USER, ROLE_ADMIN
                .build();

        return new InMemoryUserDetailsManager(user, admin);
    }

    /**
     * DelegatingPasswordEncoder。ハッシュの先頭に {bcrypt} のような ID が付き、
     * 将来アルゴリズムを乗り換えられるようになっている（デフォルトは bcrypt）。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
