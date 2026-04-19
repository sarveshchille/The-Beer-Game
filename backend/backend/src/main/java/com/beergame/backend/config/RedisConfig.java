package com.beergame.backend.config;

import com.beergame.backend.dto.GameStateDTO;
import com.beergame.backend.dto.RoomResultDTO;
import com.beergame.backend.dto.RoomStateDTO;
import com.beergame.backend.service.GameStateSubscriber;
import com.beergame.backend.service.RoomResultSubscriber;
import com.beergame.backend.service.RoomStateSubscriber;
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

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        return template;
    }

    /** Listener adapter for game-updates:{gameId} → forwards GameStateDTO */
    @Bean
    MessageListenerAdapter gameListenerAdapter(GameStateSubscriber subscriber) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "receiveMessage");
        adapter.setSerializer(new Jackson2JsonRedisSerializer<>(GameStateDTO.class));
        return adapter;
    }

    /** Listener adapter for room-updates:{roomId} → forwards RoomStateDTO */
    @Bean
    MessageListenerAdapter roomListenerAdapter(RoomStateSubscriber subscriber) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "receiveMessage");
        adapter.setSerializer(new Jackson2JsonRedisSerializer<>(RoomStateDTO.class));
        return adapter;
    }

    /** Listener adapter for room-result:{roomId} → forwards RoomResultDTO (winner announcement) */
    @Bean
    MessageListenerAdapter roomResultListenerAdapter(RoomResultSubscriber subscriber) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "receiveMessage");
        adapter.setSerializer(new Jackson2JsonRedisSerializer<>(RoomResultDTO.class));
        return adapter;
    }

    @Bean
    RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter gameListenerAdapter,
            MessageListenerAdapter roomListenerAdapter,
            MessageListenerAdapter roomResultListenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        container.addMessageListener(gameListenerAdapter,       new PatternTopic("game-updates:*"));
        container.addMessageListener(roomListenerAdapter,       new PatternTopic("room-updates:*"));
        container.addMessageListener(roomResultListenerAdapter, new PatternTopic("room-result:*"));

        return container;
    }
}