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
}
