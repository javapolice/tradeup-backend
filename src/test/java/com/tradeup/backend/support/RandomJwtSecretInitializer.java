package com.tradeup.backend.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/** Supplies an isolated, ephemeral JWT secret for each Spring test context. */
public class RandomJwtSecretInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        applicationContext.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("randomTestJwtSecret", Map.of(
                        "TRADEUP_JWT_SECRET_BASE64", Base64.getEncoder().encodeToString(keyBytes))));
    }
}
