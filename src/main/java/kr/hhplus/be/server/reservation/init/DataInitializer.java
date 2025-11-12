package kr.hhplus.be.server.reservation.init;

import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ReservationRepository reservationRepository;

    private final SeatJpaRepository seatJpaRepository;

    @Override
    public void run(String... args) throws Exception {
        // 앱 실행 시 딱 한 번 실행
        // 만료된 예약 & 좌석 데이터 생성
        List<Reservation> reservationList = new ArrayList<>();
        List<Seat> seatList = new ArrayList<>();
        Seat seat;

        int totalCount = 10000; //10만건은 너무 많아서 처리가 느려서 1만건으로 함
        double expiredRatio = 0.8; // 만료 80%, 정상 20%

        for(int i = 0; i < totalCount; i++) {
            Long concertId = (long)(1 + Math.random() * 10); // concertId 1 ~ 10 사이
            int seatNumber = (int)(1 + Math.random() * 100); // seatNum 1 ~ 100
            int price = (int)(50000 + Math.random() * 100000); // price 5만 0~ 15만원

            seat = new Seat(
                    null,
                    concertId,
                    seatNumber,
                    price
            );

            // 만료 여부 결정
            boolean isExpired = Math.random() < expiredRatio;
            LocalDateTime expiredAt = isExpired
                    ? LocalDateTime.now().minusMinutes((long)(1 + Math.random() * 20)) // 이미 만료된
                    : LocalDateTime.now().plusMinutes((long)(1 + Math.random() * 10)); // 아직 유효한

            seat.assign(expiredAt); // 만료 or 유효
            seatList.add(seat);

            reservationList.add(new Reservation(
                    "userId" + i,
                    concertId,
                    seat.getSeatId(), // seat은 아직 null
                    expiredAt,
                    price,
                    seatNumber
            ));
        }

        seatJpaRepository.saveAll(seatList);
        reservationRepository.saveAll(reservationList);

        System.out.println("[Reservation.DataInitializer] 만료된 예약 & 좌석 더미 데이터 생성 완료 : 총 " + totalCount + "건 (만료 80%, 정상 20%)");

    }
}
