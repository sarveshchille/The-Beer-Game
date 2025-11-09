package com.beergame.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
public class RedisTests {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void testSendMail() {
        redisTemplate.opsForValue().set("vinamra", "vinamrat4@gmail.com");
        Object email = redisTemplate.opsForValue().get("vinamra");
        System.out.println("Fetched from Redis: " + email);
    }
}