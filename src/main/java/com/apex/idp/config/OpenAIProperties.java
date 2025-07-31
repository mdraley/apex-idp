package com.apex.idp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration properties for OpenAI integration.
 * Provides type-safe configuration with validation.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "openai")
@Validated
public class OpenAIProperties {

    /**
     * API configuration
     */
    @NotNull
    private Api api = new Api();

    /**
     * Model to use for completions
     */
    @NotBlank
    private String model = "gpt-3.5-turbo";

    /**
     * Temperature for response generation (0.0 - 2.0)
     */
    @Min(0)
    @Max(2)
    private double temperature = 0.7;

    /**
     * Maximum tokens in response
     */
    @Min(1)
    @Max(4096)
    private int maxTokens = 2000;

    /**
     * Request timeout in seconds
     */
    @Min(10)
    @Max(300)
    private int timeout = 60;

    /**
     * Maximum content length before truncation
     */
    @Min(100)
    @Max(10000)
    private int maxContentLength = 4000;

    /**
     * Retry configuration
     */
    @NotNull
    private Retry retry = new Retry();

    @Data
    public static class Api {
        /**
         * OpenAI API key
         */
        @NotBlank
        private String key;

        /**
         * OpenAI API base URL
         */
        @NotBlank
        private String url = "https://api.openai.com/v1";
    }

    @Data
    public static class Retry {
        /**
         * Maximum number of retry attempts
         */
        @Min(0)
        @Max(5)
        private int maxAttempts = 3;

        /**
         * Initial backoff delay in milliseconds
         */
        @Min(100)
        @Max(5000)
        private long backoffDelay = 1000;

        /**
         * Backoff multiplier for exponential backoff
         */
        @Min(1.0)
        @Max(3.0)
        private double backoffMultiplier = 2.0;

        /**
         * Maximum backoff delay in milliseconds
         */
        @Min(1000)
        @Max(60000)
        private long maxBackoffDelay = 10000;
    }

    /**
     * Model-specific configurations
     */
    @Data
    public static class ModelConfig {
        private String name;
        private int contextWindow;
        private double costPer1kTokens;
    }

    /**
     * Get model configuration for cost tracking
     */
    public ModelConfig getModelConfig() {
        ModelConfig config = new ModelConfig();
        switch (model) {
            case "gpt-4":
                config.setName("gpt-4");
                config.setContextWindow(8192);
                config.setCostPer1kTokens(0.03);
                break;
            case "gpt-3.5-turbo":
                config.setName("gpt-3.5-turbo");
                config.setContextWindow(4096);
                config.setCostPer1kTokens(0.0015);
                break;
            case "gpt-3.5-turbo-16k":
                config.setName("gpt-3.5-turbo-16k");
                config.setContextWindow(16384);
                config.setCostPer1kTokens(0.003);
                break;
            default:
                config.setName(model);
                config.setContextWindow(4096);
                config.setCostPer1kTokens(0.002);
        }
        return config;
    }
}