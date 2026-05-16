package org.wodrol.brakoffpc.desktop;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesktopLauncherSupportTest {

    @Test
    void shouldReadServerPortFromApplicationArguments() {
        int serverPort = DesktopLauncherSupport.resolveServerPort(
                new String[]{"--server.port=9090"},
                new Properties(),
                Map.of()
        );

        assertEquals(9090, serverPort);
    }

    @Test
    void shouldFallBackToDefaultPortWhenValueIsInvalid() {
        Properties properties = new Properties();
        properties.put("server.port", "abc");

        int serverPort = DesktopLauncherSupport.resolveServerPort(
                new String[0],
                properties,
                Map.of("SERVER_PORT", "also-invalid")
        );

        assertEquals(8080, serverPort);
    }
}
