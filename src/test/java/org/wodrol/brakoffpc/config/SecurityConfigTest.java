package org.wodrol.brakoffpc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+yw6YAAAAASUVORK5CYII="
    );
    private static final Path MOBILE_CONFIG_QR_PATH = createMobileConfigQrFile();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("app.mobile-config-qr-url", () -> MOBILE_CONFIG_QR_PATH.toString());
    }

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

    @Test
    void mobileConfigQrEndpointServesConfiguredHostFileWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/assets/mobile-config-qr"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG_BYTES));
    }

    private static Path createMobileConfigQrFile() {
        try {
            Path file = Files.createTempFile("brakoff-mobile-config-qr-", ".png");
            Files.write(file, PNG_BYTES);
            file.toFile().deleteOnExit();
            return file;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Nie udalo sie przygotowac testowego pliku QR.", exception);
        }
    }
}
