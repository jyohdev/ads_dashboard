package com.adsdashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AdsDashboardApplication {

	public static void main(String[] args) {
		SpringApplication.run(AdsDashboardApplication.class, args);
	}

}
