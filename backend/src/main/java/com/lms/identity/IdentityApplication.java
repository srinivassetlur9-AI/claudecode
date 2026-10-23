package com.lms.identity;

import com.lms.identity.config.LmsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.lms") // Module 1 (identity) and Module 2 (commerce)
@EntityScan(basePackages = "com.lms")
@EnableJpaRepositories(basePackages = "com.lms")
@EnableAsync
@EnableConfigurationProperties(LmsProperties.class)
public class IdentityApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
