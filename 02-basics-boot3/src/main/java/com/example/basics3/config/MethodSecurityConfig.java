package com.example.basics3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;

/**
 * メソッドセキュリティ（{@code @PreAuthorize} など）の式評価をカスタマイズする設定。
 *
 * <h2>ROLE 階層とメソッドセキュリティの関係</h2>
 * <ul>
 *   <li>URL 認可（{@code authorizeHttpRequests}）は、コンテキストにある {@link RoleHierarchy} Bean を自動で使う。</li>
 *   <li>メソッドセキュリティの式評価は {@link MethodSecurityExpressionHandler} が担当する。
 *       <b>Spring Security 6.3 以降は、ここでも RoleHierarchy Bean が自動的に使われる</b>
 *       （このモジュール = 6.5 では、本クラスを削除しても
 *        {@code MethodSecurityTest#roleHierarchyAppliesToMethodSecurity} が通ることを確認済み）。</li>
 *   <li>それより古いバージョン（Boot 2.7 = Security 5.8 など）では自動では効かないため、
 *       下のように ExpressionHandler へ明示的に渡す必要があった。</li>
 * </ul>
 *
 * つまりこのクラスは 6.5 では実質 no-op だが、
 * 「自前の式（{@code @PreAuthorize("hasPermission(...)")} など）を追加したい」場合の
 * 差し込み口がここだと分かるので、サンプルとして残している。
 *
 * <p>Bean メソッドを {@code static} にしている理由: メソッドセキュリティの初期化はコンテナの
 * かなり早い段階で走るため、設定クラス自身のインスタンス化を待たせない（循環参照を避ける）ため。
 */
@Configuration
public class MethodSecurityConfig {

    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
