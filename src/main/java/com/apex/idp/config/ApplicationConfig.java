package com.apex.idp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application configuration class.
 * Enables various Spring Boot features and configuration properties.
 */
@Configuration
@EnableConfigurationProperties({
        OpenAIProperties.class
})
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
public class ApplicationConfig {

    // Additional application-wide configuration beans can be defined here

}