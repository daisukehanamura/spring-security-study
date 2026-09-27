package com.example.basics3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 3.5 (Spring Security 6.5) の書き方を確認するための学習用アプリ。
 *
 * 01-basics-boot27 と同じ題材を 3.x の書き方で実装しつつ、
 * 「複数 SecurityFilterChain」「ROLE 階層」「メソッドセキュリティ」まで踏み込んでいる。
 *
 * 起動: ./mvnw spring-boot:run   （http://localhost:8081）
 */
@SpringBootApplication
public class Boot3BasicsApplication {

    public static void main(String[] args) {
        SpringApplication.run(Boot3BasicsApplication.class, args);
    }
}
