package kr.hhplus.be.server.common;

public class RedisKeyUtils {
    private static final String USER_TOKEN_MAPPING_KEY = "queue:user:token:";
    private static final String QUEUE_TOKEN_KEY = "queue:token:";
    private static final String QUEUE_LOCK_KEY = "queue:lock";
    private static final String ACTIVE_QUEUE_KEY = "queue:active";
    private static final String ACTIVE_USER_KEY = "queue:active:user:";
    private static final String WAITING_QUEUE_KEY = "queue:waiting";
    private static final String WEEKLY_RANKING_KEY = "ranking:weekly";

    // 사용자-토큰 매핑 키
    public static String userTokenKey(String userId) {
        return USER_TOKEN_MAPPING_KEY + userId;
    }

    // 토큰 자체 키
    public static String queueTokenKey(String token) {
        return QUEUE_TOKEN_KEY + token;
    }

    // 대기열 용 락 키
    public static String queueLockKey() {
        return QUEUE_LOCK_KEY;
    }

    // 좌석 예약용 락
    public static String seatLockKey(Long concertId, Integer seatNumber) {
        return String.format("lock:seat:%d:%d", concertId, seatNumber);
    }

    // active 대기열 키
    public static String activeQueueKey() {
        return ACTIVE_QUEUE_KEY;
    }

    public static String activeUserKey(String userId) {
        return ACTIVE_USER_KEY + userId;
    }

    // waiting 대기열 키
    public static String waitingQueueKey() {
        return WAITING_QUEUE_KEY;
    }

    // 콘서트 매진 랭킹
    public static String weeklyRankingKey() {
        return WEEKLY_RANKING_KEY;
    }
}
