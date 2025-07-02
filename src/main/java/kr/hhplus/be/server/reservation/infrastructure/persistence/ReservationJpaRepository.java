package kr.hhplus.be.server.reservation.infrastructure.persistence;

import kr.hhplus.be.server.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationJpaRepository extends JpaRepository<Reservation, String> {
    List<Reservation> findByUserId(String userId);
}
