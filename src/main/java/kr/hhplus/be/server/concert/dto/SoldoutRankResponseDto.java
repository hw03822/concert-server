package kr.hhplus.be.server.concert.dto;

import kr.hhplus.be.server.concert.domain.SoldoutRank;

public class SoldoutRankResponseDto {
    private Long rankingId;
    private Long concertId;
    private Double score;

    public static SoldoutRankResponseDto from (SoldoutRank result) {
        SoldoutRankResponseDto dto = new SoldoutRankResponseDto();
        dto.rankingId = result.getRankingId();
        dto.concertId = result.getConcertId();
        dto.score = result.getScore();
        return dto;
    }

}
