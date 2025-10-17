package kr.hhplus.be.server.reservation.scheduler;

import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationRepository;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;


@SpringBootTest
@ActiveProfiles("test")
public class ReservationSchedulerPerformanceTest {

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    @DisplayName("만료된 예약 & 좌석 검색 성능 테스트 - 인덱스 X")
    void findByStatusAndExpiredReservationAndSeat() {
        long startTime = System.currentTimeMillis();

        List<Reservation> expired = reservationRepository.findByStatusAndExpiredAtBefore(
                Reservation.ReservationStatus.TEMPORARILY_ASSIGNED,
                LocalDateTime.now()
        );

        long endTime = System.currentTimeMillis();
        System.out.println("[ReservationSchedulerPerformanceTest] (인덱스 없음) 만료 예약 조회 개수 : " + expired.size() +
                " , 검색 시간(ms): "+ (endTime - startTime));

    }

}
