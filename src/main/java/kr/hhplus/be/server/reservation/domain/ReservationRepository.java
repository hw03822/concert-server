package kr.hhplus.be.server.reservation.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository {
    Reservation save(Reservation reservation);
    Optional<Reservation> findById(String reservationId);
    List<Reservation> findByStatusAndExpiredAtBefore(
            Reservation.ReservationStatus status,
            LocalDateTime expiredAt
    );
    void saveAll(List<Reservation> reservations);
}
