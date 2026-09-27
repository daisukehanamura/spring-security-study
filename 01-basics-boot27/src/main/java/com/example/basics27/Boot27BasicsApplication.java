package com.example.basics27;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 2.7 (Spring Security 5.8) の書き方を確認するための学習用アプリ。
 *
 * <pre>
 * 起動方法:
 *   ./mvnw spring-boot:run                              -> コンポーネント方式(推奨・5.7以降の書き方)
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=adapter
 *                                                       -> WebSecurityConfigurerAdapter 方式(旧来の書き方)
 * </pre>
 *
 * どちらのプロファイルでも「アプリの振る舞いは同じ」になるように書いてあるので、
 * 2つの設定クラスを読み比べるのが、このモジュールの目的です。
 */
@SpringBootApplication
public class Boot27BasicsApplication {

    public static void main(String[] args) {
        SpringApplication.run(Boot27BasicsApplication.class, args);
    }
}
