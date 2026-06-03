package org.wodrol.brakoffpc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.sqlite.path=/tmp/brakoffpc-security-test.db",
        "app.security.operator.username=test-operator",
        "app.security.operator.password=test-password",
        "app.security.mobile.token=test-mobile-token"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void panelRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/login")));
    }

    @Test
    void mobileApiRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/delivery/current"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mobileApiRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mobileApiAcceptsConfiguredToken() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-mobile-token"))
                .andExpect(status().isOk());
    }
}
