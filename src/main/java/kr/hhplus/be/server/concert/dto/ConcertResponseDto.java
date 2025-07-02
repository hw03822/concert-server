package kr.hhplus.be.server.concert.dto;

import kr.hhplus.be.server.concert.domain.Concert;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ConcertResponseDto {
    private Long concertId;
    private String title;
    private String artist;
    private LocalDateTime concertAt;

    // 콘서트 엔터티로부터 생성하는 정적 팩토리 메서드
    public static ConcertResponseDto from(Concert concert){
        return ConcertResponseDto.builder()
                .concertId(concert.getConcertId())
                .title(concert.getTitle())
                .artist(concert.getArtist())
                .concertAt(concert.getConcertAt())
                .build();
    }

}
