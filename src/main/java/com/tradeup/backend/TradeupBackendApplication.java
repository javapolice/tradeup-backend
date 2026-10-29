package com.tradeup.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(
		exclude = UserDetailsServiceAutoConfiguration.class
)
public class TradeupBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeupBackendApplication.class, args);
	}

}
