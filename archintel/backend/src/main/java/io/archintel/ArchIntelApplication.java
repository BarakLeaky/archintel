package io.archintel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ArchIntelApplication {
    public static void main(String[] args) { SpringApplication.run(ArchIntelApplication.class, args); }
}
