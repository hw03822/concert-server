package kr.hhplus.be.server.common;

public class RedisKeyUtils {
    private static final String USER_TOKEN_MAPPING_KEY = "queue:user:token:";
    private static final String QUEUE_TOKEN_KEY = "queue:token:";
    private static final String LOCK_QUEUE_KEY = "lock:queue";
    private static final String ACTIVE_USERS_KEY = "queue:active";
    private static final String WAITING_QUEUE_KEY = "queue:waiting";

    // 사용자-토큰 매핑 키
    public static String userTokenKey(Long userId) {
        return USER_TOKEN_MAPPING_KEY + userId;
    }

    // 토큰 자체 키
    public static String queueTokenKey(String token) {
        return QUEUE_TOKEN_KEY + token;
    }

    // 대기열 용 락 키
    public static String queueLockKey() {
        return LOCK_QUEUE_KEY;
    }

    // 좌석 예약용 락
    public static String seatLockKey(Long concertId, Integer seatNumber) {
        return String.format("lock:seat:%d:%d", concertId, seatNumber);
    }

    // 활성 사용자 키
    public static String activeUsersKey() {
        return ACTIVE_USERS_KEY;
    }

    // 대기열 키
    public static String waitingQueueKey() {
        return WAITING_QUEUE_KEY;
    }
}
