package kr.hhplus.be.server.seat.controller;

import kr.hhplus.be.server.seat.dto.SeatResponseDto;
import kr.hhplus.be.server.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 좌석 관련 HTTP 요청을 처리하는 컨트롤러
 * 
 * @author hhplus
 * @version 1.0
 */
@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    /**
     * 특정 공연의 모든 좌석 정보를 조회합니다.
     *
     * @param concertId 조회할 공연의 ID
     * @return 해당 공연의 모든 좌석 정보 목록
     */
    @GetMapping("/concert/{concertId}")
    public ResponseEntity<List<SeatResponseDto>> getSeatsByConcertId(@PathVariable Long concertId) {
        List<SeatResponseDto> seats = seatService.getSeatsByConcertId(concertId);
        return ResponseEntity.ok(seats);
    }
}
