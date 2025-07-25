package kr.hhplus.be.server.concert.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SoldoutRank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rankingId;

    private Long concertId;
    private long ticketOpenedAt;
    private long soldoutAt;
    private int seatTotal;
    private long score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder
    public SoldoutRank(Long concertId, long ticketOpenedAt,
                       long soldoutAt, int seatTotal, long score) {
        this.concertId = concertId;
        this.ticketOpenedAt = ticketOpenedAt;
        this.soldoutAt = soldoutAt;
        this.seatTotal = seatTotal;
        this.score = score;
    }

}
