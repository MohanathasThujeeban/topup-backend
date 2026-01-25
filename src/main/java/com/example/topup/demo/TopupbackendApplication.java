package com.example.topup.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.topup.demo")
@EnableMongoRepositories(
    basePackages = "com.example.topup.demo.repository",
    excludeFilters = @ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.REGEX,
        pattern = ".*KickbackCampaign.*|.*KickbackEarning.*|.*KickbackParticipation.*"
    )
)
@EnableJpaRepositories(
    basePackages = "com.example.topup.demo.repository",
    includeFilters = @ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.REGEX,
        pattern = ".*KickbackCampaign.*|.*KickbackEarning.*|.*KickbackParticipation.*"
    )
)
public class TopupbackendApplication {

	public static void main(String[] args) {
		// Force IPv4 to avoid DNS resolution issues with MongoDB Atlas on Windows
		System.setProperty("java.net.preferIPv4Stack", "true");
		System.setProperty("java.net.preferIPv6Addresses", "false");
		
		// Improve DNS caching for better MongoDB connection stability
		System.setProperty("networkaddress.cache.ttl", "60");
		System.setProperty("networkaddress.cache.negative.ttl", "10");
		
		SpringApplication.run(TopupbackendApplication.class, args);
	}

}
