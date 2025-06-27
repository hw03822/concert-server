package kr.hhplus.be.server.reservation.dto;

import kr.hhplus.be.server.reservation.application.output.ReserveSeatResult;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ReservationResponseDto {
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

    public static ReservationResponseDto from(ReserveSeatResult result) {
        ReservationResponseDto dto = new ReservationResponseDto();
        dto.reservationId = result.getReservationId();
        dto.seatId = result.getSeatId();
        dto.concertId = result.getConcertId();
        dto.userId = result.getUserId();
        dto.seatNum = result.getSeatNum();
        dto.price = result.getPrice();
        dto.concertTitle = result.getConcertTitle();
        dto.concertAt = result.getConcertAt();
        dto.confirmedAt = result.getConfirmedAt();
        dto.expiredAt = result.getExpiredAt();
        dto.remainingTimeSeconds = result.getRemainingTimeSeconds();
        return dto;
    }
}
