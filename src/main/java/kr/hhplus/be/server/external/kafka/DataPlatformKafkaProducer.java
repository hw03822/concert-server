package kr.hhplus.be.server.external.kafka;

import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationRepository;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import kr.hhplus.be.server.reservation.listener.ReservationEventListener;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataPlatformKafkaProducer {
    private static final Logger log = LoggerFactory.getLogger(DataPlatformKafkaProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ReservationRepository reservationRepository;

    /**
     * 예약 정보 데이터 플랫폼 전송 (Kafka)
     * @param event
     */
    public void sendDataPlatform(ReservationCompletedEvent event) {
        try {
            // 1. 예약 정보 DB 에서 조회
            Reservation reservation = reservationRepository.findById(event.getReservationId())
                    .orElseThrow(() -> new IllegalStateException("예약 정보를 찾지 못했습니다."));

            // 2. kafka 전송
            kafkaTemplate.send("reservation-topic", reservation);

            log.info("예약 정보 전송 성공 (kafka) - reservationId : {}", event.getReservationId());
        } catch (Exception e) {
            log.info("예약 정보 전송 성공 (kafka) - reservationId : {}", event.getReservationId(),e);
        }

    }
}
