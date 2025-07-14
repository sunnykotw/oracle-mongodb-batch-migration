package com.example.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Oracle to MongoDB 批次遷移系統主要應用程式
 * 
 * @author Migration Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
@EnableTransactionManagement
@EnableConfigurationProperties
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationApplication.class, args);
    }
}
