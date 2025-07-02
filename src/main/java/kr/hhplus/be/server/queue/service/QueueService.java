package kr.hhplus.be.server.queue.service;

import kr.hhplus.be.server.common.RedisKeyUtils;
import kr.hhplus.be.server.common.lock.RedisDistributedLock;
import kr.hhplus.be.server.queue.domain.QueueToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class QueueService {
    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisDistributedLock redisDistributedLock;

    // 활성 사용자 최대수
    @Value("${queue.max-active-users:100}")
    private int maxActiveUsers;

    // 토큰만료시간(분)
    @Value("${queue.token-expire-minutes:30}")
    private int tokenExpireMinutes;

    // 사용자별 대기 시간(초)
    @Value("${queue.wait-time-per-user:20}")
    private int waitTimePerUser;

    // 락 TTL (데드락 방지)
    @Value("{queue.lock-timeout-seconds:5")
    private int lockTimeoutSeconds;

    // Redis 키 패턴

    public QueueService(RedisTemplate<String, Object> redisTemplate, RedisDistributedLock redisDistributedLock) {
        this.redisTemplate = redisTemplate;
        this.redisDistributedLock = redisDistributedLock;
    }

    /**
     * 대기열 토큰 발급
     * @param userId 사용자Id
     * @return 발급된 토큰 정보
     */
    public QueueToken issueToken(String userId) {

        // 기존 토큰 확인
        QueueToken existingToken = findExistingToken(userId);
        if(existingToken != null && !existingToken.isExpired()) {
            log.info("기존 토큰 반환: userID={}, token={}", userId, existingToken.getToken());
            return existingToken;
        }

        // 분산 락 획득 시도 (동시성 문제)
        String lockValue = UUID.randomUUID().toString();

        if(!redisDistributedLock.tryLock(RedisKeyUtils.queueLockKey(), lockValue, lockTimeoutSeconds)) {
            throw new RuntimeException("대기열 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        String token;
        QueueToken queueToken;
        try {
            // 현재 활성 사용자 수 확인
            Long activeUserCount = redisTemplate.opsForSet().size(RedisKeyUtils.activeUsersKey());
            log.info("현재 활성 사용자 수 : {}, 최대 허용 : {}", activeUserCount, maxActiveUsers);


            token = UUID.randomUUID().toString();
            LocalDateTime nowTime = LocalDateTime.now();

            // 활성 사용자 최대치 미만 -> 활성화된 토큰 / 최대치인 경우 -> 대기열 추가, 대기 토큰 발급
            if (activeUserCount != null && activeUserCount < maxActiveUsers){ // 활성 사용자가 최대치 미만인 경우
                // 활성화된 토큰 발급
                queueToken = new QueueToken(token, userId, 0L, 0,
                        QueueToken.QueueStatus.ACTIVE, nowTime, nowTime.plusMinutes(tokenExpireMinutes));

                // 활성 사용자 목록에 추가
                // set에 사용자 추가
                redisTemplate.opsForSet().add(RedisKeyUtils.activeUsersKey(), userId);
                log.info("{}({}) 사용자가 활성 상태로 등록되었습니다.", userId, token);
            } else { // 최대치인 경우
                // 대기열에 추가
                Double score = (double) System.currentTimeMillis();
                redisTemplate.opsForZSet().add(RedisKeyUtils.waitingQueueKey(), userId, score);

                // 대기 순서 계산
                Long position = redisTemplate.opsForZSet().rank(RedisKeyUtils.waitingQueueKey(), userId);
                position = (position != null) ? position + 1 : 1; // rank는 0부터 시작

                Integer estimatedWaitTime = (int) (position * waitTimePerUser / 60); // 분 단위

                // 대기 토큰 발급
                queueToken = new QueueToken(token, userId, position, estimatedWaitTime,
                        QueueToken.QueueStatus.WAITING, nowTime, nowTime.plusMinutes(tokenExpireMinutes));

                log.info("대기열 추가 : userId={}, position={}, waitTime={}분", userId, position, estimatedWaitTime);
            }

        } finally {
            // 분산 락 해제
            releaseLock(RedisKeyUtils.queueLockKey(), lockValue);
        }

        // 토큰 정보 Redis에 저장
        redisTemplate.opsForValue().set(RedisKeyUtils.queueTokenKey(token), queueToken,
                tokenExpireMinutes, TimeUnit.MINUTES);

        // 사용자-토큰 매핑 저장 (기존 토큰 찾기용)
        redisTemplate.opsForValue().set(RedisKeyUtils.userTokenKey(userId), token,
                tokenExpireMinutes, TimeUnit.MINUTES);

        log.info("토큰 발급 완료 : userId={}, token={}, status={}", userId, token, queueToken.getStatus());

        return queueToken;
    }

    /**
     * 기존 토큰 찾기
     * @param userId 사용자Id
     * @return 기존 토큰 정보 or null (최초 요청)
     */
    private QueueToken findExistingToken(String userId) {
        String userTokenKey = RedisKeyUtils.userTokenKey(userId);
        String existingToken = (String) redisTemplate.opsForValue().get(userTokenKey);

        if(existingToken != null) {
            String queueTokenKey = RedisKeyUtils.queueTokenKey(existingToken);
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(queueTokenKey);

            return QueueToken.builder()
                    .token(queueTokenKey)
                    .userId(entries.get("userId").toString())
                    .queuePosition(Long.parseLong(entries.get("queuePosition").toString()))
                    .estimatedWaitTimeMinutes(Integer.parseInt(entries.get("estimatedWaitTimeMinutes").toString()))
                    .status(QueueToken.QueueStatus.valueOf(entries.get("status").toString()))
                    .issuedAt(LocalDateTime.parse(entries.get("issuedAt").toString()))
                    .expiresAt(LocalDateTime.parse(entries.get("expiresAt").toString()))
                    .build();
        }

        return null;
    }

    /**
     * 락 해제
     * @param lockKey 락 Key
     * @param lockValue 락 value
     */
    private void releaseLock(String lockKey, String lockValue) {
        if(Objects.equals(redisTemplate.opsForValue().get(lockKey), lockValue)){
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 대기열 상태 조회
     * @param token 대기열 토큰
     * @return 현재 대기열 상태
     */
    public QueueToken getQueueStatus(String token) {
        QueueToken queueToken = (QueueToken) redisTemplate.opsForValue().get(RedisKeyUtils.queueTokenKey(token));
        if(queueToken == null) {
            throw new IllegalStateException("유효하지 않은 토큰입니다.");
        }

        // 만료된 토큰인지 확인
        if(queueToken.isExpired()) {
            throw new IllegalStateException("만료된 토큰입니다.");
        }

        // 대기 중인 경우 순서 업데이트
        if(queueToken.getStatus() == QueueToken.QueueStatus.WAITING) {
            Long position = redisTemplate.opsForZSet().rank(RedisKeyUtils.waitingQueueKey(), queueToken.getUserId());
            if(position != null) {
                position = position + 1; // rank는 0부터 시작
                Integer estimatedWaitTimeMinutes = (int) (position * waitTimePerUser / 60);
                queueToken.updatePosition(position, estimatedWaitTimeMinutes);

                // Redis에 업데이트된 정보 저장
                redisTemplate.opsForValue().set(RedisKeyUtils.queueTokenKey(queueToken.getToken()), queueToken,
                        tokenExpireMinutes, TimeUnit.MINUTES);
            }

        }

        log.info("토큰 상태 조회 완료 : token={}, status={}, position={}, estimatedWaitTimeMinutes={}",
                token, queueToken.getStatus(), queueToken.getQueuePosition(), queueToken.getEstimatedWaitTimeMinutes());

        return queueToken;
    }

    /**
     * 활성 토큰 유효성 검증 (좌석 예약시)
     * @param token 대기열 토큰
     * @return 유효한 활성 토큰인지 여부
     */
    public boolean validateActiveToken(String token) {
        if(token == null) {
            return false;
        }

        try {
            QueueToken queueToken = getQueueStatus(token);
            return queueToken.isActive();
        } catch (Exception e) {
            return false;
        }

    }

}
