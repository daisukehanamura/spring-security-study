package com.example.basics3;

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

/** 画面側（webFilterChain）の URL 認可と ROLE 階層の確認。 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("画面の URL 認可")
class WebAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("未認証はログイン画面へリダイレクト")
    void anonymous() throws Exception {
        mockMvc.perform(get("/whoami"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER は /admin/** と /manager/** に入れない")
    void userCannotAccessHigherRoles() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/manager/approvals")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("MANAGER は /manager/** に入れるが /admin/** は入れない")
    void managerScope() throws Exception {
        mockMvc.perform(get("/manager/approvals")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("★ROLE 階層: ADMIN は MANAGER 用の URL にもそのまま入れる")
    void roleHierarchyAppliesToUrlAuthorization() throws Exception {
        mockMvc.perform(get("/manager/approvals")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("hasAuthority は ROLE_ が付かない権限で判定される")
    void authorityBasedRule() throws Exception {
        // ROLE_USER は持っているが report:read は持っていないので 403
        mockMvc.perform(get("/reports/list")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "report:read")
    @DisplayName("report:read があれば /reports/** に入れる")
    void authorityGranted() throws Exception {
        mockMvc.perform(get("/reports/list")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "report:read")
    @DisplayName("allOf(...) の複合条件は片方だけでは通らない")
    void compositeRuleNeedsBoth() throws Exception {
        mockMvc.perform(get("/reports/export/csv")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"report:read", "ROLE_MANAGER"})
    @DisplayName("allOf(...) の両方を満たせば認可は通る（ハンドラが無いので 404 になる = 通過した証拠）")
    void compositeRuleSatisfied() throws Exception {
        mockMvc.perform(get("/reports/export/csv")).andExpect(status().isNotFound());
    }
}
