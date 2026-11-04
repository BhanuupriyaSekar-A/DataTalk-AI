package com.datatalk.datatalkai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Small smoke test for login, role-based access and the ADMIN-only analytics API.
 * Needs the Neon database (DB_URL / DB_USER / DB_PASSWORD) and the demo users from sample-data.sql.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Login and access smoke tests")
class LoginAndAccessIntegrationTest {

    private static final String DEMO_PASSWORD = "DemoPass@123";

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession login(String username) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/login").contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getRequest().getSession();
    }

    @Test
    void loginReturnsUserIdentityAndScope() throws Exception {
        MockHttpSession session = login("techpay_customer");
        mockMvc.perform(get("/api/user-info").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("techpay_customer"))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.customerId").value(1))
                .andExpect(jsonPath("$.data.userId").exists());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/login").contentType("application/json")
                        .content("{\"username\":\"admin_user\",\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void apisRequireLogin() throws Exception {
        mockMvc.perform(get("/api/user-info")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/analytics")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanOpenQueryAnalytics() throws Exception {
        MockHttpSession session = login("admin_user");
        mockMvc.perform(get("/api/admin/analytics?days=30").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.total_questions").exists());
        mockMvc.perform(get("/analytics").session(session)).andExpect(status().isOk());
    }

    @Test
    void nonAdminsCannotOpenQueryAnalytics() throws Exception {
        for (String user : new String[]{"techpay_manager", "retail_manager", "techpay_customer"}) {
            MockHttpSession session = login(user);
            mockMvc.perform(get("/api/admin/analytics").session(session)).andExpect(status().isForbidden());
            mockMvc.perform(get("/analytics").session(session)).andExpect(status().isForbidden());
        }
    }

    @Test
    void customerDoesNotSeeSettlementReports() throws Exception {
        mockMvc.perform(get("/api/reports").session(login("admin_user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].category", hasItem("settlement")));
        mockMvc.perform(get("/api/reports").session(login("techpay_customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].category", everyItem(not("settlement"))));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = login("admin_user");
        mockMvc.perform(post("/api/logout").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/user-info").session(new MockHttpSession())).andExpect(status().isUnauthorized());
    }
}
