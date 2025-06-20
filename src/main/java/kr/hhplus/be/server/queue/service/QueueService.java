package kr.hhplus.be.server.queue.service;

import kr.hhplus.be.server.queue.domain.QueueToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class QueueService {
    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final RedisTemplate<String, Object> redisTemplate;

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
    private static final String USER_TOKEN_MAPPING_KEY = "queue:user:token:";
    private static final String QUEUE_TOKEN_KEY = "queue:token:";
    private static final String QUEUE_LOCK_KEY = "queue:lock";
    private static final String ACTIVE_USERS_KEY = "queue:active";
    private static final String WAITING_QUEUE_KEY = "queue:waiting";

    public QueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
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
        boolean lockAcquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                QUEUE_LOCK_KEY,
                lockValue,
                lockTimeoutSeconds,
                TimeUnit.SECONDS
        ));

        if(!lockAcquired) {
            throw new RuntimeException("대기열 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        String token;
        QueueToken queueToken;
        try {
            // 현재 활성 사용자 수 확인
            Long activeUserCount = redisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
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
                redisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId);
                log.info("{}({}) 사용자가 활성 상태로 등록되었습니다.", userId, token);
            } else { // 최대치인 경우
                // 대기열에 추가
                Double score = (double) System.currentTimeMillis();
                redisTemplate.opsForZSet().add(WAITING_QUEUE_KEY, userId, score);

                // 대기 순서 계산
                Long position = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userId);
                position = (position != null) ? position + 1 : 1; // rank는 0부터 시작

                Integer estimatedWaitTime = (int) (position * waitTimePerUser / 60); // 분 단위

                // 대기 토큰 발급
                queueToken = new QueueToken(token, userId, position, estimatedWaitTime,
                        QueueToken.QueueStatus.WAITING, nowTime, nowTime.plusMinutes(tokenExpireMinutes));

                log.info("대기열 추가 : userId={}, position={}, waitTime={}분", userId, position, estimatedWaitTime);
            }

        } finally {
            // 락 해제
            releaseLock(QUEUE_LOCK_KEY, lockValue);
        }

        // 토큰 정보 Redis에 저장
        redisTemplate.opsForValue().set(QUEUE_TOKEN_KEY + token, queueToken,
                tokenExpireMinutes, TimeUnit.MINUTES);

        // 사용자-토큰 매핑 저장 (기존 토큰 찾기용)
        redisTemplate.opsForValue().set(USER_TOKEN_MAPPING_KEY + userId, token,
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
        String userTokenKey = USER_TOKEN_MAPPING_KEY + userId;
        String existingToken = (String) redisTemplate.opsForValue().get(userTokenKey);

        if(existingToken != null) {
            String queueTokenKey = QUEUE_TOKEN_KEY + existingToken;
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
     * @param lockKey
     * @param lockValue
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
        QueueToken queueToken = (QueueToken) redisTemplate.opsForValue().get(QUEUE_TOKEN_KEY + token);
        if(queueToken == null) {
            throw new IllegalStateException("유효하지 않은 토큰입니다.");
        }

        // 만료된 토큰인지 확인
        if(queueToken.isExpired()) {
            throw new IllegalStateException("만료된 토큰입니다.");
        }

        // 대기 중인 경우 순서 업데이트
        if(queueToken.getStatus() == QueueToken.QueueStatus.WAITING) {
            Long position = redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, queueToken.getUserId());
            Integer estimatedWaitTimeMinutes = (int) (position * waitTimePerUser / 60);
            queueToken.updatePosition(position, estimatedWaitTimeMinutes);

            // Redis에 업데이트된 정보 저장
            redisTemplate.opsForValue().set(QUEUE_TOKEN_KEY + queueToken.getToken(), queueToken,
                    tokenExpireMinutes, TimeUnit.MINUTES);
        }

        log.info("토큰 상태 조회 완료 : token={}, status={}, position={}, estimatedWaitTimeMinutes={}",
                token, queueToken.getStatus(), queueToken.getQueuePosition(), queueToken.getEstimatedWaitTimeMinutes());

        return queueToken;
    }

    /**
     * 대기열에서 사용자를 활성화
     */
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void activateWaitingUsers() {
        log.info("대기 중인 사용자 활성화 프로세스 시작");

        // 분산 락 획득 시도
        String lockValue = UUID.randomUUID().toString();
        boolean lockAcquired = acquireLock(QUEUE_LOCK_KEY, lockValue, lockTimeoutSeconds);

        if (!lockAcquired) {
            log.debug("다른 프로세스에서 활성화 작업 진행 중");
            return;
        }

        try {
            activateWaitingUsersWithLock();
        } finally {
            releaseLock(QUEUE_LOCK_KEY, lockValue);
        }
    }

    private void activateWaitingUsersWithLock() {
        // 만료된 사용자들 먼저 정리
        cleanupExpiredUsers();

        Long activeUserCount = redisTemplate.opsForSet().size(ACTIVE_USERS_KEY);
        long availableSlots = maxActiveUsers - (activeUserCount != null ? activeUserCount : 0);

        if (availableSlots <= 0) {
            log.info("활성화 가능한 슬롯 없음: activeUsers={}, maxActive={}", activeUserCount, maxActiveUsers);
            return;
        }

        // 대기열에서 가장 오래 기다린 사용자들 가져오기
        Set<Object> waitingUsers = redisTemplate.opsForZSet().range(WAITING_QUEUE_KEY, 0, availableSlots - 1);

        if (waitingUsers == null || waitingUsers.isEmpty()) {
            log.info("대기 중인 사용자 없음");
            return;
        }

        for (Object userIdObj : waitingUsers) {
            String userId = (String) userIdObj;

            // 대기열에서 제거
            redisTemplate.opsForZSet().remove(WAITING_QUEUE_KEY, userId);

            // 활성 사용자로 추가 (개별 expire 관리)
            String userToken = (String) redisTemplate.opsForValue().get(USER_TOKEN_MAPPING_KEY + userId);
            if (userToken != null) {
                LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(tokenExpireMinutes);
                addActiveUserWithIndividualExpire(userId, userToken, expiresAt);

                // 해당 사용자의 토큰 활성화
                activateUserToken(userId, userToken);
            }

            log.info("사용자 활성화 완료: userId={}", userId);
        }

        log.info("대기 중인 사용자 활성화 완료: activatedCount={}", waitingUsers.size());
    }

    /**
     * 락 획득
     */
    private boolean acquireLock(String lockKey, String lockValue, int timeoutSeconds) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                timeoutSeconds,
                TimeUnit.SECONDS
        ));
    }

    /**
     * 만료된 사용자들 정리
     */
    private void cleanupExpiredUsers() {
        Set<Object> activeUsers = redisTemplate.opsForSet().members(ACTIVE_USERS_KEY);
        if (activeUsers == null || activeUsers.isEmpty()) {
            return;
        }

        for (Object userIdObj : activeUsers) {
            String userId = (String) userIdObj;
            String userToken = (String) redisTemplate.opsForValue().get(USER_TOKEN_MAPPING_KEY + userId);
            
            if (userToken != null) {
                QueueToken token = (QueueToken) redisTemplate.opsForValue().get(QUEUE_TOKEN_KEY + userToken);
                if (token != null && token.isExpired()) {
                    // 만료된 사용자 제거
                    redisTemplate.opsForSet().remove(ACTIVE_USERS_KEY, userId);
                    redisTemplate.delete(USER_TOKEN_MAPPING_KEY + userId);
                    redisTemplate.delete(QUEUE_TOKEN_KEY + userToken);
                    log.info("만료된 사용자 제거: userId={}", userId);
                }
            }
        }
    }

    /**
     * 개별 만료 시간으로 활성 사용자 추가
     */
    private void addActiveUserWithIndividualExpire(String userId, String userToken, LocalDateTime expiresAt) {
        redisTemplate.opsForSet().add(ACTIVE_USERS_KEY, userId);
        
        // 개별 사용자 만료 시간 설정
        long ttlSeconds = java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        if (ttlSeconds > 0) {
            redisTemplate.expire(ACTIVE_USERS_KEY + ":" + userId, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * 사용자 토큰 활성화
     */
    private void activateUserToken(String userId, String userToken) {
        QueueToken token = (QueueToken) redisTemplate.opsForValue().get(QUEUE_TOKEN_KEY + userToken);
        if (token != null) {
            token.activate();
            redisTemplate.opsForValue().set(QUEUE_TOKEN_KEY + userToken, token, tokenExpireMinutes, TimeUnit.MINUTES);
        }
    }
}
