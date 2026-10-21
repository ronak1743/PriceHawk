package com.pricehawk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class PriceHawkApplication {
    public static void main(String[] args) {
        loadEnvIfPresent();
        SpringApplication.run(PriceHawkApplication.class, args);
    }

    private static void loadEnvIfPresent() {
        Path[] searchPaths = new Path[]{
            Path.of(".env"),
            Path.of("backend/.env"),
            Path.of("../.env")
        };

        for (Path p : searchPaths) {
            if (Files.isRegularFile(p)) {
                try {
                    List<String> lines = Files.readAllLines(p);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        if (eqIdx > 0) {
                            String key = line.substring(0, eqIdx).trim();
                            String val = line.substring(eqIdx + 1).trim();
                            // Do not override system or environment properties already passed
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, val);
                            }
                        }
                    }
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }
}

