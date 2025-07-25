package kr.hhplus.be.server.concert.controller;

import kr.hhplus.be.server.concert.domain.SoldoutRank;
import kr.hhplus.be.server.concert.dto.SoldoutRankResponseDto;
import kr.hhplus.be.server.concert.service.ConcertRankingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 매진 랭킹 관리 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/concerts/ranking")
public class ConcertRankingController {

    private final ConcertRankingService concertRankingService;

    public ConcertRankingController(ConcertRankingService concertRankingService) {
        this.concertRankingService = concertRankingService;
    }

    /**
     * 매진 랭킹 조회
     * GET /api/v1/concerts/ranking
     */
    @GetMapping
    public ResponseEntity<List<SoldoutRank>> getRanking(
            @RequestParam(defaultValue = "30") int limit
    ) {
//        List<SoldoutRank> result = concertRankingService.getTopRankings(limit); // cache 사용x
        List<SoldoutRank> result = concertRankingService.getSoldOutRankingCache(limit); // cache 사용o

        return ResponseEntity.ok(result);
    }
}
