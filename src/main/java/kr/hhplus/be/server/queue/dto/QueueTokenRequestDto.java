package kr.hhplus.be.server.queue.dto;

import lombok.Getter;

/**
 * 대기열 토큰 발급 요청 DTO
 */
@Getter
public class QueueTokenRequestDto {
    private String userId;
}
