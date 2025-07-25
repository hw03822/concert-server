package kr.hhplus.be.server.reservation.event;

import lombok.Getter;

@Getter
public class ReservationCompletedEvent {
    private final String reservationId;

    public ReservationCompletedEvent(String reservationId) {
        this.reservationId = reservationId;
    }
}
