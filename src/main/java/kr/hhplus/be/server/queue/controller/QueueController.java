package kr.hhplus.be.server.queue.controller;

import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.queue.dto.QueueTokenRequestDto;
import kr.hhplus.be.server.queue.dto.QueueTokenResponseDto;
import kr.hhplus.be.server.queue.service.QueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 대기열 관리 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {
    private final QueueService queueService;

    public QueueController(QueueService queueService){
        this.queueService = queueService;
    }

    /**
     * 대기열 토큰 발급
     * POST /api/v1/queue/token
     */
    @PostMapping("/token")
    public ResponseEntity<QueueTokenResponseDto> issueToken(@RequestBody QueueTokenRequestDto request) {
        QueueToken queueToken = queueService.issueToken(request.getUserId());
        QueueTokenResponseDto response = QueueTokenResponseDto.from(queueToken);

        return ResponseEntity.ok(response);
    }

    /**
     * 대기열 상태 조회
     * GET /api/v1/queue/status
     */
    @GetMapping("/status")
    public ResponseEntity<QueueTokenResponseDto> getQueueStatus(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);

        QueueToken queueToken = queueService.getQueueStatus(token);
        QueueTokenResponseDto response = QueueTokenResponseDto.from(queueToken);

        return ResponseEntity.ok(response);
    }

    /**
     * Authorization 헤더에서 토큰 추출
     */
    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("유효하지 않은 Authorization 헤더입니다.");
        }
        return authHeader.substring(7);
    }

}
