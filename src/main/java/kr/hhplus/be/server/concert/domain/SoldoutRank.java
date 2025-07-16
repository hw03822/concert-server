package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
public class SoldoutRank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rankingId;

    private Long concertId;
    private LocalDateTime ticketOpenedAt;
    private LocalDateTime soldoutAt;
    private int seatTotal;
    private Double score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
