package com.apex.idp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.context.annotation.Bean;

/**
 * Configuration for Spring Retry functionality.
 * Enables @Retryable annotation support for automatic retry logic.
 */
@Configuration
@EnableRetry
public class RetryConfig {

    /**
     * Default retry template for programmatic retry operations.
     * Can be injected and used where @Retryable annotation is not suitable.
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Configure retry policy
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Configure backoff policy
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(1000L); // 1 second
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    /**
     * Custom retry template for OpenAI operations with exponential backoff.
     */
    @Bean(name = "openAiRetryTemplate")
    public RetryTemplate openAiRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Configure retry policy
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Configure exponential backoff
        org.springframework.retry.backoff.ExponentialBackOffPolicy backOffPolicy =
                new org.springframework.retry.backoff.ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L); // 1 second
        backOffPolicy.setMaxInterval(10000L); // 10 seconds
        backOffPolicy.setMultiplier(2.0); // Double the wait time each retry
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}