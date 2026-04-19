package com.beergame.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Allow up to 35 seconds for Render cold start
        factory.setConnectTimeout(35_000);
        factory.setReadTimeout(35_000);

        return new RestTemplate(factory);
    }

}
