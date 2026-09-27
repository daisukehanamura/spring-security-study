package com.example.basics27;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 認可ルールのテスト（共通部分）。
 *
 * このクラスを 2 つのプロファイル（adapter 方式 / コンポーネント方式）で継承して動かすことで、
 * 「書き方を変えても振る舞いは同じ」ことをテストで担保している。
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractSecurityRulesTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("トップページは未ログインでも見られる")
    void indexIsPublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("未ログインで画面にアクセスするとログイン画面へリダイレクトされる(302)")
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/whoami"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("未ログインで API にアクセスすると 401(リダイレクトではない)")
    void anonymousApiReturns401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    @DisplayName("ROLE_USER は /whoami を見られる")
    void userCanAccessWhoami() throws Exception {
        mockMvc.perform(get("/whoami")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    @DisplayName("ROLE_USER は /admin/** にアクセスできない(403) … URL ベースの認可")
    void userCannotAccessAdminUrl() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    @DisplayName("ROLE_ADMIN は /admin/** にアクセスできる")
    void adminCanAccessAdminUrl() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    @DisplayName("URL は自由でも @PreAuthorize で弾かれる … メソッドセキュリティ")
    void methodSecurityBlocksUser() throws Exception {
        mockMvc.perform(get("/report/admin-only")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HTTP Basic で API を呼べる / 権限が足りなければ 403")
    void httpBasicOnApi() throws Exception {
        mockMvc.perform(get("/api/me").with(httpBasic("user", "password")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/stats").with(httpBasic("user", "password")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/stats").with(httpBasic("admin", "password")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me").with(httpBasic("user", "wrong")))
                .andExpect(status().isUnauthorized());
    }
}
