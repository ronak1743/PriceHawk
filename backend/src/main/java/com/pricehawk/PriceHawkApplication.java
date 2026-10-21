package com.pricehawk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PriceHawkApplication {
    public static void main(String[] args) {
        SpringApplication.run(PriceHawkApplication.class, args);
    }
}
