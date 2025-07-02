package kr.hhplus.be.server.reservation.controller;

import kr.hhplus.be.server.reservation.application.ReservationService;
import kr.hhplus.be.server.reservation.application.input.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.application.output.ReserveSeatResult;
import kr.hhplus.be.server.reservation.dto.ReservationRequestDto;
import kr.hhplus.be.server.reservation.dto.ReservationResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 예약 관리 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * 좌석 예약
     * POST /api/v1/reservations
     */
    @PostMapping
    public ResponseEntity<ReservationResponseDto> reserveSeat(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ReservationRequestDto request) {

        String token = extractToken(authHeader);

        ReserveSeatCommand command = new ReserveSeatCommand(
                request.getUserId(),
                request.getConcertId(),
                request.getSeatNumber()
        );

        ReserveSeatResult result = reservationService.reserveSeat(command, token);
        ReservationResponseDto response = ReservationResponseDto.from(result);

        return ResponseEntity.ok(response);
    }

    /**
     * 좌석 예약 상태 조회
     * GET /api/v1/reservations/{reservationId}
     */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponseDto> getReservationStatus(@PathVariable String reservationId) {
        ReserveSeatResult result = reservationService.getReservationStatus(reservationId);
        ReservationResponseDto response = ReservationResponseDto.from(result);

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
