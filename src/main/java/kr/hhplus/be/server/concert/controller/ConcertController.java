package kr.hhplus.be.server.concert.controller;

import kr.hhplus.be.server.concert.dto.ConcertResponseDto;
import kr.hhplus.be.server.concert.service.ConcertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    /**
     * 예약 가능한 콘서트 날짜 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<ConcertResponseDto>> getAvailableConcerts() {
        return ResponseEntity.ok(concertService.getAvailableConcerts());
    }
}
