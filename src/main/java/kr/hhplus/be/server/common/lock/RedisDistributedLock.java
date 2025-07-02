package kr.hhplus.be.server.common.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RedisDistributedLock {

    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLock.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisDistributedLock(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryLock(String key, String value, long timeoutSeconds) {
        try {
            Boolean lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, value, timeoutSeconds, TimeUnit.SECONDS);

            boolean result = Boolean.TRUE.equals(lockAcquired);

            if(result) {
                logger.debug("분산 락 획득 성공: key={}, value={}", key, value);
            } else {
                logger.debug("분산 락 획득 실패: key={}, value={}", key, value);
            }

            return result;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 락 해제
     * @param lockKey
     * @param lockValue
     */
    public void releaseLock(String lockKey, String lockValue) {
        if(Objects.equals(redisTemplate.opsForValue().get(lockKey), lockValue)){
            redisTemplate.delete(lockKey);
        }
    }
}
