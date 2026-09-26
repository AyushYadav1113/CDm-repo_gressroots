package com.grassroots.cdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Entry point for the Certificate Deployment Manager (CDM) application.
 *
 * CDM orchestrates certificate discovery, renewal matching, and lifecycle deployments
 * via secure MID Server execution channels.
 */
@SpringBootApplication
public class CdmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CdmApplication.class, args);
    }
}
