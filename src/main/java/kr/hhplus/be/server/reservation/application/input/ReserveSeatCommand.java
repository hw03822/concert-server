package kr.hhplus.be.server.reservation.application.input;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReserveSeatCommand {
    private String userId;
    private Long concertId;
    private Integer seatNumber;
}
