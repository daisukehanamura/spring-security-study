package com.example.basics3;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** API 側（apiFilterChain）の確認。こちらはステートレス + Basic 認証 + CSRF 無効。 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("API のフィルタチェーン")
class ApiAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("未認証の API は 401（画面と違いリダイレクトしない）")
    void unauthenticatedApiReturns401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("permitAll の API は認証なしで通る")
    void publicApi() throws Exception {
        mockMvc.perform(get("/api/public/ping")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET は USER でも可、POST は ADMIN のみ（HTTP メソッド単位の認可）")
    void methodLevelRules() throws Exception {
        mockMvc.perform(get("/api/orders").with(httpBasic("user", "password")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders").with(httpBasic("user", "password")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/orders").with(httpBasic("admin", "password")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CSRF トークン無しの POST が通る = このチェーンでは CSRF 無効")
    void csrfDisabledOnApiChain() throws Exception {
        mockMvc.perform(post("/api/orders").with(httpBasic("admin", "password")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("@PostFilter: user には自分の注文だけが返る")
    void postFilterHidesOthersOrders() throws Exception {
        mockMvc.perform(get("/api/orders").with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].owner").value("user"));
    }

    @Test
    @DisplayName("@PostFilter: admin には全件返る")
    void adminSeesAllOrders() throws Exception {
        mockMvc.perform(get("/api/orders").with(httpBasic("admin", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @DisplayName("@PostAuthorize: 他人の注文を直接指定すると 403")
    void postAuthorizeBlocksOthersOrder() throws Exception {
        mockMvc.perform(get("/api/orders/1").with(httpBasic("user", "password")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/orders/4").with(httpBasic("user", "password")))
                .andExpect(status().isForbidden());
    }
}
