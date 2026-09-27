package com.example.basics3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;

import com.example.basics3.service.OrderService;

/**
 * メソッドセキュリティは Web を経由しなくても効く（＝バッチや他サービス経由の呼び出しも守れる）。
 * サービスを直接呼ぶテストにすることで、それが確認できる。
 */
@SpringBootTest
@DisplayName("メソッドセキュリティ")
class MethodSecurityTest {

    @Autowired
    OrderService orderService;

    @Test
    @DisplayName("未認証だと @PreAuthorize で弾かれる（※例外の型が 403 系と違う点に注意）")
    void anonymousIsDenied() {
        // ★ここは知っておくと得: SecurityContext に Authentication が無い状態で
        //   メソッドセキュリティに到達すると AccessDeniedException ではなく
        //   AuthenticationCredentialsNotFoundException（AuthenticationException のサブクラス）になる。
        //   Web 経由なら ExceptionTranslationFilter が拾って 401 相当の処理をするが、
        //   バッチなど Web の外から呼ぶ場合は自分でハンドリングする必要がある。
        assertThatThrownBy(() -> orderService.findAll())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    @DisplayName("USER は findAll(ADMIN 限定) を呼べない")
    void userCannotCallAdminMethod() {
        assertThatThrownBy(() -> orderService.findAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    @DisplayName("MANAGER は approve できる")
    void managerCanApprove() {
        assertThat(orderService.approve(3).approved()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("★ROLE 階層がメソッドセキュリティにも効く（ADMIN で MANAGER 限定メソッドを呼べる）")
    void roleHierarchyAppliesToMethodSecurity() {
        assertThat(orderService.approve(1).approved()).isTrue();
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    @DisplayName("引数と認証情報の突き合わせ（#owner == authentication.name）")
    void argumentBasedRule() {
        assertThat(orderService.findByOwner("user")).hasSize(2);
        assertThatThrownBy(() -> orderService.findByOwner("admin"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
