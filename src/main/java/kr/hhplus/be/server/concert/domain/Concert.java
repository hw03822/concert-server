package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
public class Concert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long concertId;

    private String title;
    private String artist;

    private int seatTotal;
    private LocalDateTime openTime;
    private LocalDateTime soldOutTime;

    private LocalDateTime concertAt;
    private LocalDateTime createdAt;

    // 기본 생성자 (JPA 필수)
    protected Concert() {}

    // 생성자
    public Concert(Long concertId, String title, String artist, LocalDateTime concertAt) {
        this.concertId = concertId;
        this.title = title;
        this.artist = artist;
        this.concertAt = concertAt;
        this.createdAt = LocalDateTime.now();
    }

    // 랭킹 시스템 테스트용 생성자
    public Concert(Long concertId, String title, String artist,
                   int seatTotal, LocalDateTime openTime, LocalDateTime concertAt) {
        this.concertId = concertId;
        this.title = title;
        this.artist = artist;
        this.seatTotal = seatTotal;
        this.openTime = openTime;
        this.concertAt = concertAt;
        this.createdAt = LocalDateTime.now();
    }
}
