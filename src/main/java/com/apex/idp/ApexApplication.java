package com.apex.idp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ApexApplication {
	public static void main(String[] args) {
		SpringApplication.run(ApexApplication.class, args);
	}
}
