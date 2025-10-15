package kr.hhplus.be.server.reservation.scheduler;

import kr.hhplus.be.server.reservation.application.ReservationService;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReservationSchedulerTest {
    private static final Logger log = LoggerFactory.getLogger(ReservationSchedulerTest.class);

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private ReservationScheduler reservationScheduler;

    private Seat seat;
    private Reservation reservation;

    @BeforeEach
    void setup() {
        String userId = "expiredUser";
        Long concertId = 1L;

        // 만료된 좌석 정보 생성
        seat = new Seat(null, concertId, 1, 150000);
        seat.assign(LocalDateTime.now().minusMinutes(10)); // 10분 전에 이미 만료됨
        seat = seatJpaRepository.save(seat); // 저장하고 반환값으로 재할당

        // 만료된 예약 정보 생성
        reservation = new Reservation(
                userId,
                concertId,
                seat.getSeatId(),
                LocalDateTime.now().minusMinutes(10),
                seat.getPrice(),
                seat.getSeatNumber()
        );
        reservation = reservationRepository.save(reservation); // 저장하고 반환값으로 재할당
    }

    @Test
    @DisplayName("만료된 예약들에 대해 스케줄러가 자동으로 예약 만료로 상태 변경 및 좌석 배정 해제한다.")
    void reservationTimeoutScheduler_shouldReleaseSeatAndExpireReservation() {
        // given

        // when
        reservationScheduler.releaseExpiredReservationsScheduler();

        // then
        // 예약 상태 -> EXPIRED
        // 좌석 상태 -> AVAILABLE , 만료상태 isExpired()
        Reservation updatedReservation = reservationRepository.findById(reservation.getReservationId()).orElseThrow();
        Seat updatedSeat = seatJpaRepository.findById(seat.getSeatId()).orElseThrow();

        assertThat(updatedReservation.getStatus()).isEqualTo(Reservation.ReservationStatus.EXPIRED);
        assertThat(updatedSeat.getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);

    }

}