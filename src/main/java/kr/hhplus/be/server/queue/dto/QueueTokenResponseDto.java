package kr.hhplus.be.server.queue.dto;

import kr.hhplus.be.server.queue.domain.QueueToken;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 대기열 토큰 응답 DTO
 */
@Getter
public class QueueTokenResponseDto {
    private String token;
    private String userId;
    private Long queuePosition;
    private Integer estimatedWaitTimeMinutes;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;

    public static QueueTokenResponseDto from(QueueToken queueToken){
        QueueTokenResponseDto dto = new QueueTokenResponseDto();
        dto.token = queueToken.getToken();
        dto.userId = queueToken.getUserId();
        dto.queuePosition = queueToken.getQueuePosition();
        dto.estimatedWaitTimeMinutes = queueToken.getEstimatedWaitTimeMinutes();
        dto.status = queueToken.getStatus().name();
        dto.issuedAt = queueToken.getIssuedAt();
        dto.expiresAt = queueToken.getExpiresAt();
        return dto;
    }
}
