package kr.hhplus.be.server.reservation.dto;

import lombok.Getter;

@Getter
public class ReservationRequestDto {
    private String userId;
    private Long concertId;
    private Integer seatNumber;
}
