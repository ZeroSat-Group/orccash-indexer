package com.ordinalssync.orccash.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;

public class RedisDistributedLock {
    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);
    private static final int RETRY_TIMES = 10;
    private static final Duration SLEEP = Duration.ofMillis(500L);
    private static final Duration TIMEOUT = Duration.ofMillis(15000L);
    private static final String UNLOCK_LUA = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then     return redis.call(\"del\",KEYS[1]) else     return 0 end ";
    private static final ThreadLocal<String> LOCK_FLAG = new ThreadLocal();

    public RedisDistributedLock() {
    }

    public static boolean lock(RedisTemplate<?, ?> redisTemplate, String key) {
        return lock(redisTemplate, key, TIMEOUT, 10, SLEEP);
    }

    public static boolean lock(RedisTemplate<?, ?> redisTemplate, String key, int retryTimes) {
        return lock(redisTemplate, key, TIMEOUT, retryTimes, SLEEP);
    }

    public static boolean lock(RedisTemplate<?, ?> redisTemplate, String key, int retryTimes, Duration sleep) {
        return lock(redisTemplate, key, TIMEOUT, retryTimes, sleep);
    }

    public static boolean lock(RedisTemplate<?, ?> redisTemplate, String key, Duration expire) {
        return lock(redisTemplate, key, expire, 10, SLEEP);
    }

    public static boolean lock(RedisTemplate<?, ?> redisTemplate, String key, Duration expire, int retryTimes) {
        return lock(redisTemplate, key, expire, retryTimes, SLEEP);
    }

    public static boolean lock(RedisTemplate<?, ?> redisTemplate, String key, Duration expire, int retryTimes, Duration sleep) {
        boolean result;
        for(result = setRedis(redisTemplate, key, expire); !result && retryTimes-- > 0; result = setRedis(redisTemplate, key, expire)) {
            try {
                log.debug("lock failed, retrying..." + retryTimes);
                Thread.sleep(sleep.toMillis());
            } catch (InterruptedException var7) {
                return false;
            }
        }

        return result;
    }

    private static boolean setRedis(RedisTemplate<?, ?> redisTemplate, final String key, final Duration expire) {
        try {
            RedisCallback<Boolean> callback = (connection) -> {
                String uuid = UUID.randomUUID().toString();
                LOCK_FLAG.set(uuid);
                return connection.set(key.getBytes(StandardCharsets.UTF_8), uuid.getBytes(StandardCharsets.UTF_8), Expiration.from(expire), SetOption.SET_IF_ABSENT);
            };
            Boolean rst = (Boolean)redisTemplate.execute(callback);
            return rst != null && rst;
        } catch (Exception var5) {
            log.error("redis lock error.", var5);
            return false;
        }
    }

    public static boolean releaseLock(RedisTemplate<?, ?> redisTemplate, String key) {
        try {
            RedisCallback<Boolean> callback = (connection) -> {
                String value = (String)LOCK_FLAG.get();
                return (Boolean)connection.eval("if redis.call(\"get\",KEYS[1]) == ARGV[1] then     return redis.call(\"del\",KEYS[1]) else     return 0 end ".getBytes(), ReturnType.BOOLEAN, 1, new byte[][]{key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8)});
            };
            Boolean rst = (Boolean)redisTemplate.execute(callback);
            boolean var4 = rst != null && rst;
            return var4;
        } catch (Exception var8) {
            log.error("release lock occurred an exception", var8);
        } finally {
            LOCK_FLAG.remove();
        }

        return false;
    }
}
