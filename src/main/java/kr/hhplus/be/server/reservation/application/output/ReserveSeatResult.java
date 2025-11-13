package kr.hhplus.be.server.reservation.application.output;

import kr.hhplus.be.server.reservation.domain.Reservation;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReserveSeatResult {
    private String reservationId;
    private Long seatId;
    private Long concertId;
    private String userId;
    private Integer seatNum;
    private Long price;
    private String concertTitle;
    private LocalDateTime concertAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime expiredAt;
    private long remainingTimeSeconds;

    public ReserveSeatResult(Reservation reservation) {
        this.reservationId = reservation.getReservationId();
        this.seatId = reservation.getSeatId();
        this.concertId = reservation.getConcertId();
        this.userId = reservation.getUserId();
        this.seatNum = reservation.getSeatNum();
        this.price = reservation.getPrice();
        this.concertTitle = reservation.getConcertTitle();
        this.concertAt = reservation.getConcertAt();
        this.confirmedAt = reservation.getConfirmedAt();
        this.expiredAt = reservation.getExpiredAt();
        this.remainingTimeSeconds = reservation.getRemainingTimeSeconds();
    }
}
