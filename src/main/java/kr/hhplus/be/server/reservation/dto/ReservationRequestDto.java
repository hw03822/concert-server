package kr.hhplus.be.server.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReservationRequestDto {
    private String userId;
    private Long concertId;
    private Integer seatNumber;
}
