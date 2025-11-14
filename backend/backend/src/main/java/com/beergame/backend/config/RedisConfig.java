package com.beergame.backend.config;

import com.beergame.backend.dto.GameStateDTO;
import com.beergame.backend.dto.RoomStateDTO; // 👈 --- ADD THIS IMPORT
import com.beergame.backend.service.GameStateSubscriber;
import com.beergame.backend.service.RoomStateSubscriber; // 👈 --- You will create this
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

    // This bean is fine
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // ... (your existing code is correct) ...
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        return template;
    }

    /**
     * ✅ FIX: Listener adapter for GAME state
     */
    @Bean
    MessageListenerAdapter gameListenerAdapter(GameStateSubscriber subscriber) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "receiveMessage");
        // Tell it to deserialize GameStateDTO
        adapter.setSerializer(new Jackson2JsonRedisSerializer<>(GameStateDTO.class));
        return adapter;
    }

    /**
     * ✅ FIX: Listener adapter for ROOM state
     */
    @Bean
    MessageListenerAdapter roomListenerAdapter(RoomStateSubscriber subscriber) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "receiveMessage");
        // Tell it to deserialize RoomStateDTO
        adapter.setSerializer(new Jackson2JsonRedisSerializer<>(RoomStateDTO.class));
        return adapter;
    }

    @Bean
    RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
            MessageListenerAdapter gameListenerAdapter, // 👈 --- Inject game adapter
            MessageListenerAdapter roomListenerAdapter // 👈 --- Inject room adapter
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // ✅ FIX: Subscribe each adapter to its OWN topic
        container.addMessageListener(gameListenerAdapter, new PatternTopic("game-updates:*"));
        container.addMessageListener(roomListenerAdapter, new PatternTopic("room-updates:*"));

        return container;
    }
}