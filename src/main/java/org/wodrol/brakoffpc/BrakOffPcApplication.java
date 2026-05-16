package org.wodrol.brakoffpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.wodrol.brakoffpc.desktop.DesktopLauncherSupport;

@SpringBootApplication
public class BrakOffPcApplication {

    public static void main(String[] args) {
        if (DesktopLauncherSupport.tryOpenExistingInstance(args)) {
            return;
        }
        SpringApplication.run(BrakOffPcApplication.class, args);
    }

}
