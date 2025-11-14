package com.beergame.backend.config;

import com.beergame.backend.dto.GameStateDTO;
import com.beergame.backend.service.GameStateSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // This bean is for PUBLISHING messages
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        // Use JSON serializer for the game state object
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));

        return template;
    }

    // This adapter connects our subscriber logic (GameStateSubscriber) to Redis
    @SuppressWarnings("null")
    @Bean
    MessageListenerAdapter messageListenerAdapter(GameStateSubscriber subscriber) {
        // "receiveMessage" is the method name in GameStateSubscriber that will be
        // called
        return new MessageListenerAdapter(subscriber, "receiveMessage");
    }

    // This bean is for SUBSCRIBING to channels
    @SuppressWarnings("null")
    @Bean
    RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
            MessageListenerAdapter messageListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Add a listener to a "topic" (channel pattern)
        // This will listen to all channels that start with "game-updates:"
        container.addMessageListener(messageListenerAdapter,
                new PatternTopic("game-updates:*"));

        container.addMessageListener(messageListenerAdapter,
                new PatternTopic("room-updates:*"));

        return container;
    }
}
