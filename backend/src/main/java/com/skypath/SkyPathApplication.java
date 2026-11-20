package com.skypath;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the SkyPath flight connection search service.
 */
@SpringBootApplication
public class SkyPathApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyPathApplication.class, args);
    }
}
