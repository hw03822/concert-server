package kr.hhplus.be.server.external.dataplatform.request;

import kr.hhplus.be.server.reservation.domain.Reservation;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public class ReservatoinSendRequestDto {
    private String reservationId;
    private Long seatId;
    private Long concertId;
    private String userId;
    private Integer seatNum;
    private Integer price;
    private String concertTitle;
    private LocalDateTime concertAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime expiredAt;
    private long remainingTimeSeconds;

    public static ReservatoinSendRequestDto of(Reservation reservation) {
        return ReservatoinSendRequestDto.builder()
                .reservationId(reservation.getReservationId())
                .seatId(reservation.getSeatId())
                .concertId(reservation.getConcertId())
                .userId(reservation.getUserId())
                .seatNum(reservation.getSeatNum())
                .price(reservation.getPrice())
                .concertTitle(reservation.getConcertTitle())
                .concertAt(reservation.getConcertAt())
                .confirmedAt(reservation.getConfirmedAt())
                .expiredAt(reservation.getExpiredAt())
                .remainingTimeSeconds(reservation.getRemainingTimeSeconds())
                .build();
    }
}
