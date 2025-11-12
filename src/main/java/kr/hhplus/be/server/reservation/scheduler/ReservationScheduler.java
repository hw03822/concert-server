package kr.hhplus.be.server.reservation.scheduler;

import kr.hhplus.be.server.reservation.application.ReservationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationScheduler.class);

    private final ReservationService reservationService;

    @Scheduled(fixedDelay = 300000) // 5분마다 실행
    public void releaseExpiredReservationsScheduler() {
        try {
            log.info("[ReservationScheduler] 만료된 예약 해제 스케줄러 시작");

            long startTime = System.currentTimeMillis();
            reservationService.releaseExpiredReservations();
            long endTime = System.currentTimeMillis();

            log.info("[ReservationScheduler] 만료된 예약 해제 스케줄러 완료. 소요 시간 : {}ms", endTime - startTime);
        } catch (Exception e) {
            log.info("[ReservationScheduler] 만료된 예약 해제 스케줄러 중 오류 발생", e);
        }
    }

}
