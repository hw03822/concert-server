package kr.hhplus.be.server.queue.service;

import kr.hhplus.be.server.common.RedisKeyUtils;
import kr.hhplus.be.server.common.lock.RedisDistributedLock;
import kr.hhplus.be.server.queue.domain.QueueToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
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
     * 대기열 토큰 발급 (분산 락 획득으로 동시성 문제 해결)
     * @param userId 사용자Id
     * @return 발급된 토큰 정보
     */
    public QueueToken issueTokenWithLock(String userId) {

        // 1. 기존 토큰 확인
        QueueToken existingToken = findExistingToken(userId);
        if(existingToken != null && !existingToken.isExpired()) {
            log.info("기존 토큰 반환: userID={}, token={}", userId, existingToken.getToken());
            return existingToken;
        }

        // 2. 분산 락 획득 시도 (동시성 문제)
        if(!redisDistributedLock.tryLockWithRetry(RedisKeyUtils.queueLockKey(), userId, lockTimeoutSeconds)) {
            throw new RuntimeException("대기열 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        QueueToken queueToken;
        try {
            // 3. 토큰 발급
            queueToken = issueToken(userId);
        } catch (Exception e) {
            // 4. 수동 롤백, 예외 발생 시 제거
            redisTemplate.opsForSet().remove(RedisKeyUtils.activeQueueKey(), userId);
            redisTemplate.delete(RedisKeyUtils.activeUserKey(userId));
            throw new RuntimeException("토큰 발급에 실패했습니다.");
        } finally {
            // 5. 분산 락 해제
            redisDistributedLock.releaseLock(RedisKeyUtils.queueLockKey(), userId);
        }
        return queueToken;
    }

    /**
     * 활성 사용자 수를 확인하여 즉시 활성화(활성 대기열) 하거나 대기열에 추가
     * @param userId 사용자 ID
     * @return 발급된 토큰 정보
     */
    private QueueToken issueToken(String userId) {

        // 1. 현재 활성 사용자 수 확인
        // 1-1. 만료된 사용자들 먼저 정리
        cleanupExpiredUsers();

        // 1-2. 정리 후 현재 활성 사용자 수 조회
        Long activeUserCount = redisTemplate.opsForSet().size(RedisKeyUtils.activeQueueKey());
        log.info("현재 활성 사용자 수 : {}, 최대 허용 : {}", activeUserCount, maxActiveUsers);

        // 2. 새 토큰 생성
        String token = UUID.randomUUID().toString();
        LocalDateTime nowTime = LocalDateTime.now();

        // 3. 활성 사용자 최대치 미만 -> 활성화된 토큰 / 최대치인 경우 -> 대기열 추가, 대기 토큰 발급
        QueueToken queueToken;
        if (activeUserCount != null && activeUserCount < maxActiveUsers){ // 활성 사용자가 최대치 미만인 경우
            // 활성화된 토큰 발급 (활성 대기열)
            queueToken = issueActiveToken(userId, token, nowTime);
        } else { // 최대치인 경우
            // 대기열에 추가
            queueToken = issueWaitingToken(userId, token, nowTime);
        }

        // 4. 토큰 정보 Redis에 저장
        redisTemplate.opsForValue().set(RedisKeyUtils.queueTokenKey(token), queueToken,
                tokenExpireMinutes, TimeUnit.MINUTES);

        // 5. 사용자-토큰 매핑 저장 (기존 토큰 찾기용)
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
     * 활성 사용자용 토큰 발급
     * @param userId 사용자 ID
     * @param token 생성한 토큰
     * @param nowTime 토큰 생성 일시
     * @return 발급된 토큰 정보
     */
    private QueueToken issueActiveToken(String userId, String token, LocalDateTime nowTime) {
        QueueToken queueToken = new QueueToken(token, userId, 0L, 0,
                QueueToken.QueueStatus.ACTIVE, nowTime, nowTime.plusMinutes(tokenExpireMinutes));

        // 활성 사용자 목록에 추가 + 개별 TTL 관리
        boolean addedToSet = addActiveUserWithIndividualExpire(userId, token);
        if(!addedToSet) {
            throw new RuntimeException("활성 사용자 등록 실패했습니다.");
        }

        log.info("{}({}) 사용자가 활성 상태로 등록되었습니다.", userId, token);

        return queueToken;
    }

    /**
     * 대기 사용자용 토큰 발급
     * @param userId 사용자 ID
     * @param token 생성한 토큰
     * @param nowTime 토큰 생성 일시
     * @return 발급된 토큰 정보
     */
    private QueueToken issueWaitingToken(String userId, String token, LocalDateTime nowTime) {
        // 대기열에 추가
        Double score = (double) System.currentTimeMillis();
        redisTemplate.opsForZSet().add(RedisKeyUtils.waitingQueueKey(), userId, score);

        // 대기 순서 계산
        Long position = redisTemplate.opsForZSet().rank(RedisKeyUtils.waitingQueueKey(), userId);
        position = (position != null) ? position + 1 : 1; // rank는 0부터 시작

        Integer estimatedWaitTime = (int) (position * waitTimePerUser / 60); // 분 단위

        // 대기 토큰 발급
        QueueToken queueToken = new QueueToken(token, userId, position, estimatedWaitTime,
                QueueToken.QueueStatus.WAITING, nowTime, nowTime.plusMinutes(tokenExpireMinutes));

        log.info("대기열 추가 : userId={}, position={}, waitTime={}분", userId, position, estimatedWaitTime);

        return queueToken;
    }

    /**
     * 활성 사용자를 개별 TTL과 함께 추가
     * 활성 대기열 Set에 사용자를 추가하고, 개별 TTL 관리
     * @param userId 추가할 사용자 ID
     * @param token 생성한 토큰
     */
    private boolean addActiveUserWithIndividualExpire(String userId, String token) {
        boolean addedToSet = false;

        // Set에 사용자 추가
        addedToSet = redisTemplate.opsForSet().add(RedisKeyUtils.activeQueueKey(), userId) > 0;

        // 개별 TTL 관리 (활성 대기열에 사용자가 추가된 경우에 관리)
        if(addedToSet) {
            redisTemplate.opsForValue().set(RedisKeyUtils.activeUserKey(userId), token, tokenExpireMinutes, TimeUnit.MINUTES);
        }

        return addedToSet;
    }

    /**
     * 만료된 사용자 정리
     * 개별 활성 키가 만료되었을 경우 활성 대기열(set) 에서도 제거
     */
    private void cleanupExpiredUsers() {
        // 활성 대기열 사용자 데이터 가져오기
        Set<Object> activeUsers = redisTemplate.opsForSet().members(RedisKeyUtils.activeQueueKey());
        if (activeUsers != null) {
            for (Object userIdObj : activeUsers) {
                String userId = (String) userIdObj;
                if (!redisTemplate.hasKey(RedisKeyUtils.activeUserKey(userId))) {
                    // 개별 활성 키 만료 시, 활성 대기열(set)에서 제거
                    redisTemplate.opsForSet().remove(RedisKeyUtils.activeQueueKey(), userId);
                    log.info("만료된 사용자 {} 정리", userId);
                }
            }
        }
    }

    /**
     * 대기열에서 활성 대기열로 업데이트 (분산 락 획득으로 동시성 문제 해결)
     */
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void activateWaitingUsersWithLock() {
        log.info("[Scheduler] 대기 중인 사용자 활성화 프로세스 시작");

        // 1. 분산 락 획득 시도 (동시성 문제)
        String lockValue = UUID.randomUUID().toString();
        if(!redisDistributedLock.tryLockWithRetry(RedisKeyUtils.queueLockKey(), lockValue, lockTimeoutSeconds)) {
            throw new RuntimeException("대기열 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            // 2. 대기열에서 사용자 활성화
            activateWaitingUsers();
        } finally {
            redisDistributedLock.releaseLock(RedisKeyUtils.queueLockKey(), lockValue);
        }
    }

    /**
     *대기열에서 활성 대기열로 업데이트 (사용자 활성화)
     */
    private void activateWaitingUsers() {
        // 1. 현재 이용가능한 활성화 수 확인
        // 1-1. 만료된 사용자들 먼저 정리
        cleanupExpiredUsers();

        // 1-2. 현재 활성 사용자 수 조회 및 활성화 가능 슬롯 확인
        Long activeUserCount = redisTemplate.opsForSet().size(RedisKeyUtils.activeQueueKey());
        long availableSlots = maxActiveUsers - (activeUserCount != null ? activeUserCount : 0);

        if (availableSlots <= 0) {
           log.info("활성화 가능한 슬롯이 없습니다.: activeUser = {}, maxActive = {}", activeUserCount, maxActiveUsers);
           return;
        }

        // 2. 대기열에서 가장 오래 기다린 사용자 가져오기
        Set<Object> waitingUsersObj = redisTemplate.opsForZSet().range(RedisKeyUtils.waitingQueueKey(), 0, availableSlots - 1);

        if (waitingUsersObj == null || waitingUsersObj.isEmpty()) {
            log.info("대기 중인 사용자가 없습니다.");
            return;
        }

        for (Object userIdObj : waitingUsersObj) {
            String userId = (String) userIdObj;

            // 2-1. 대기열에서 사용자 제거
            redisTemplate.opsForZSet().remove(RedisKeyUtils.waitingQueueKey(), userId);

            // 2-2. 활성 대기열에 사용자 추가 및 개별 만료 관리 추가
            String userToken = (String) redisTemplate.opsForValue().get(RedisKeyUtils.userTokenKey(userId));
            if (userToken != null) {
                addActiveUserWithIndividualExpire(userId, userToken);

                // 3. 사용자 토큰 상태 업데이트
                QueueToken queueToken = (QueueToken) redisTemplate.opsForValue().get(RedisKeyUtils.queueTokenKey(userToken));
                if (queueToken != null) {
                    queueToken.activate();
                    queueToken.updatePosition(0L, 0);

                    // 3-1. Redis 토큰 업데이트
                    redisTemplate.opsForValue().set(RedisKeyUtils.queueTokenKey(userToken), queueToken, tokenExpireMinutes, TimeUnit.MINUTES);
                }
            }
            log.info("사용자 활성화를 완료했습니다.: userId = {}", userId);
        }
        log.info("대기 중인 사용자를 모두 활성화 완료했습니다.: activatedCount = {}", waitingUsersObj.size());
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
            updateWaitingPosition(queueToken);
        }

        log.info("토큰 상태 조회 완료 : token={}, status={}, position={}, estimatedWaitTimeMinutes={}",
                token, queueToken.getStatus(), queueToken.getQueuePosition(), queueToken.getEstimatedWaitTimeMinutes());

        return queueToken;
    }

    /**
     * 대기 순서 업데이트
     * @param queueToken 토큰 정보
     */
    private void updateWaitingPosition(QueueToken queueToken) {
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
