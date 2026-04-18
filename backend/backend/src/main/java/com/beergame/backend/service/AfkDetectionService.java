package com.beergame.backend.service;

import com.beergame.backend.event.AfkOrderRequestEvent;
import com.beergame.backend.event.AllPlayersReadyEvent;
import com.beergame.backend.event.WeekStartedEvent;
import com.beergame.backend.model.BotType;
import com.beergame.backend.model.Game;
import com.beergame.backend.model.Players;
import com.beergame.backend.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Detects AFK players and submits bot orders on their behalf.
 *
 * Flow:
 *  1. When a new week starts, TurnService writes a Redis key:
 *       afk:{gameId}:{week}  with a TTL of AFK_TIMEOUT_SECONDS
 *  2. This scheduler runs every 10 seconds and checks all IN_PROGRESS games.
 *  3. If the Redis key has expired (TTL <= 0) and there are still unready
 *     players, they are treated as AFK and the EASY bot orders for them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AfkDetectionService {

    private final GameRepository  gameRepository;
    private final ApplicationEventPublisher    eventPublisher; 
    private final BotService botService;
    private final RedisTemplate<String, Object> redisTemplate;

    public static final int AFK_TIMEOUT_SECONDS = 60;
    private static final String AFK_KEY_PREFIX  = "afk:";


    // Listens for AllPlayersReadyEvent — no longer needs GameService to clear timer
    @EventListener
    public void onAllPlayersReady(AllPlayersReadyEvent event) {
        clearWeekTimer(event.getGameId(), event.getWeek());
    }


    /**
     * Called by TurnService at the START of each new week.
     * Registers the AFK deadline for this game + week.
     */
    public void registerWeekStart(String gameId, int week) {
        String key = AFK_KEY_PREFIX + gameId + ":" + week;
        redisTemplate.opsForValue().set(key, "active", AFK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        log.info("AFK timer started for game {} week {} ({}s)", gameId, week, AFK_TIMEOUT_SECONDS);
    }

    /**
     * Called when all players submit in time.
     * Clears the AFK key early so the scheduler doesn't fire unnecessarily.
     */
    public void clearWeekTimer(String gameId, int week) {
        redisTemplate.delete(AFK_KEY_PREFIX + gameId + ":" + week);
    }

    @EventListener
   public void onWeekStarted(WeekStartedEvent event) {
    String key = AFK_KEY_PREFIX + event.getGameId() + ":" + event.getWeek();
    redisTemplate.opsForValue()
        .set(key, "active", AFK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    log.info("AFK timer started for game {} week {}", event.getGameId(), event.getWeek());
   }


    /**
     * Every 10 seconds: find IN_PROGRESS games whose AFK timer has expired
     * and submit bot orders for any players who haven't ordered yet.
     */
    @Scheduled(fixedRate = 40_000)
    public void checkAfkPlayers() {
        List<Game> activeGames = gameRepository
                .findByGameStatus(Game.GameStatus.IN_PROGRESS);

        for (Game game : activeGames) {
            String key = AFK_KEY_PREFIX + game.getId() + ":" + game.getCurrentWeek();
            Boolean exists = redisTemplate.hasKey(key);

            // Key expired = AFK timeout elapsed, key still there = still waiting
            if (Boolean.TRUE.equals(exists)) continue;

            List<Players> afkPlayers = game.getPlayers().stream()
                    .filter(p -> !p.isReadyForOrder())
                    .toList();

            if (afkPlayers.isEmpty()) continue;

            log.warn("AFK timeout for game {} week {}. {} player(s) not ready — submitting bot orders.",
                    game.getId(), game.getCurrentWeek(), afkPlayers.size());

            for (Players afkPlayer : afkPlayers) {
                // Temporarily treat them as EASY bot for this turn
                afkPlayer.setBotType(BotType.EASY);
                int order = botService.calculateOrder(game, afkPlayer);
                eventPublisher.publishEvent(
                    new AfkOrderRequestEvent(this, game.getId(), afkPlayer.getUserName(), order));
            }
        }
    }
}
