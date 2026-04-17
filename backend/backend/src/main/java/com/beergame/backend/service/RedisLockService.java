package com.beergame.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cluster-safe distributed locking via Redis SETNX.
 * Replaces the JVM-only synchronized(gameId.intern()) pattern,
 * which provides zero protection across multiple server instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final long   LOCK_TIMEOUT_SECONDS = 30;
    private static final int    RETRY_DELAY_MS       = 100;
    private static final String LOCK_PREFIX          = "lock:game:";

    private boolean tryAcquire(String lockKey) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + lockKey, "locked",
                        LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    private void release(String lockKey) {
        redisTemplate.delete(LOCK_PREFIX + lockKey);
    }

    /**
     * Executes {@code action} while holding an exclusive Redis lock on {@code lockKey}.
     * Retries up to {@code maxRetries} times before throwing.
     *
     * @throws RuntimeException if the lock cannot be acquired within the retry budget
     * @throws RuntimeException (wrapping InterruptedException) if the thread is interrupted
     */
    public <T> T executeWithLock(String lockKey, int maxRetries, Supplier<T> action) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (tryAcquire(lockKey)) {
                try {
                    return action.get();
                } finally {
                    release(lockKey);
                }
            }
            log.warn("Lock '{}' busy. Attempt {}/{}", lockKey, attempt, maxRetries);
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for lock: " + lockKey, e);
            }
        }
        throw new RuntimeException(
                "Failed to acquire distributed lock for '" + lockKey
                        + "' after " + maxRetries + " attempts");
    }
}