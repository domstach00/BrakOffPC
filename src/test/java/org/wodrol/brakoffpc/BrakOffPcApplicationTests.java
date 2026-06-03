package org.wodrol.brakoffpc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.sqlite.path=/tmp/brakoffpc-context-test.db")
class BrakOffPcApplicationTests {

    @Test
    void contextLoads() {
    }

}
