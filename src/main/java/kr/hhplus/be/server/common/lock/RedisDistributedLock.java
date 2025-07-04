package kr.hhplus.be.server.common.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RedisDistributedLock {

    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLock.class);

    private final RedisTemplate<String, Object> redisTemplate;

    // 최대 재시도 가능 횟수
    @Value("${DistributedLock.max-retry-attempts:3}")
    private int maxRetryAttempts;

    // 재시도 지연 시간 (ms)
    @Value("${DistributedLock.retry-delay-ms:200}")
    private long retryDelayMs;

    public RedisDistributedLock(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 분산 락 획득 시도 with 재시도
     * @param key 락 key
     * @param value 락 value
     * @param timeoutSeconds TTL (초)
     * @return 분산 락 획득 여부
     */
    public boolean tryLockWithRetry(String key, String value, long timeoutSeconds) {
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++){
            if (tryLock(key, value, timeoutSeconds)) {
                return true;
            }

            if (attempt < maxRetryAttempts) {
                try {
                    Thread.sleep(retryDelayMs * attempt); // 백오프
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * 분산 락 획득 시도
     * @param key 락 key
     * @param value 락 value
     * @param timeoutSeconds TTL (초)
     * @return 분산 락 획득 여부
     */
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
            logger.error("분산 락 획득 중 오류 발생: key={}, value={}", key, value, e);

            return false;
        }
    }

    /**
     * 락 해제
     * @param lockKey 락 key
     * @param lockValue 락 value
     */
    public void releaseLock(String lockKey, String lockValue) {
        if(Objects.equals(redisTemplate.opsForValue().get(lockKey), lockValue)){
            redisTemplate.delete(lockKey);
        }
    }
}
