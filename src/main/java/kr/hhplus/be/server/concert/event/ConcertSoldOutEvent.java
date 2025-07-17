package kr.hhplus.be.server.concert.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
public class ConcertSoldOutEvent {
    private final Long concertId;
    private final LocalDateTime soldoutAt;

    public ConcertSoldOutEvent(Long concertId, LocalDateTime soldoutAt) {
        this.concertId = concertId;
        this.soldoutAt = soldoutAt;
    }
}
