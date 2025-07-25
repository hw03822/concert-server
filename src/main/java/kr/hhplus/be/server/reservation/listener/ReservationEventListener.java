package kr.hhplus.be.server.reservation.listener;

import kr.hhplus.be.server.external.dataplatform.DataPlatformSender;
import kr.hhplus.be.server.external.dataplatform.request.ReservatoinSendRequestDto;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationRepository;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReservationEventListener {
    private static final Logger log = LoggerFactory.getLogger(ReservationEventListener.class);

    private final ReservationRepository reservationRepository;
    private final DataPlatformSender dataPlatformSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReservationCompleted(ReservationCompletedEvent event) {
        try {
            // 1. 예약 정보 DB 에서 조회
            Reservation reservation = reservationRepository.findById(event.getReservationId())
                    .orElseThrow(() -> new IllegalStateException("예약 정보를 찾지 못했습니다."));

            // 2. dataPlatform 으로 전송하는 mock API 호출
            dataPlatformSender.send(ReservatoinSendRequestDto.of(reservation));

        } catch (Exception e) {
            log.info("예약 정보 이벤트 처리 중 오류 발생 - reservationId : {}", event.getReservationId(), e);
        }
    }

}
