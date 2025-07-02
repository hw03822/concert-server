package kr.hhplus.be.server.queue.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
public class QueueToken {
    private String token;
    private String userId;
    private Long queuePosition;
    private Integer estimatedWaitTimeMinutes;
    private QueueStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;

    public enum QueueStatus {
        WAITING, ACTIVE, EXPIRED
    }

    public QueueToken(String token, String userId, Long queuePosition, Integer estimatedWaitTimeMinutes,
                      QueueStatus status, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.token = token;
        this.userId = userId;
        this.queuePosition = queuePosition;
        this.estimatedWaitTimeMinutes = estimatedWaitTimeMinutes;
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 토큰이 활성 상태인지 확인
     */
    public boolean isActive() {
        return status == QueueStatus.ACTIVE && expiresAt.isAfter(LocalDateTime.now());
    }

    /**
      토큰이 만료되었는지 확인
     */
    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * 활성 상태 처리
     */
    public void activate(){
        this.status = QueueStatus.ACTIVE;
    }

    /**
     * 만료 상태 처리
     */
    public void expire(){
        this.status = QueueStatus.EXPIRED;
    }

    /**
     * 대기열 토큰 위치 정보 및 예상 대기 시간 갱신
     */
    public void updatePosition(Long position, Integer waitTime){
        this.queuePosition = position;
        this.estimatedWaitTimeMinutes = waitTime;
    }


}
