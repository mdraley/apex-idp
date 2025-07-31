package com.apex.idp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

/**
 * Main Spring Boot application class for APEX Intelligent Document Processing.
 *
 * This application provides AI-powered document processing capabilities
 * for hospital accounts payable automation.
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.apex.idp.config")
@EnableCaching
@ComponentScan(basePackages = {"com.apex.idp"})
public class ApexIdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApexIdpApplication.class, args);
    }

    /**
     * Application startup banner
     */
    static {
        System.out.println("""
            
             █████╗ ██████╗ ███████╗██╗  ██╗    ██╗██████╗ ██████╗ 
            ██╔══██╗██╔══██╗██╔════╝╚██╗██╔╝    ██║██╔══██╗██╔══██╗
            ███████║██████╔╝█████╗   ╚███╔╝     ██║██║  ██║██████╔╝
            ██╔══██║██╔═══╝ ██╔══╝   ██╔██╗     ██║██║  ██║██╔═══╝ 
            ██║  ██║██║     ███████╗██╔╝ ██╗    ██║██████╔╝██║     
            ╚═╝  ╚═╝╚═╝     ╚══════╝╚═╝  ╚═╝    ╚═╝╚═════╝ ╚═╝     
            
            Intelligent Document Processing v1.0.0
            Starting APEX Backend Services...
            
            """);
    }
}