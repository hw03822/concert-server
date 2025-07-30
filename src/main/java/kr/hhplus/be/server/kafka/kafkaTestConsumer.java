package kr.hhplus.be.server.kafka;

import kr.hhplus.be.server.reservation.application.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class kafkaTestConsumer {
    private static final Logger log = LoggerFactory.getLogger(kafkaTestConsumer.class);

    @KafkaListener(topics = "test-topic", groupId = "test-group")
    public void listen(String message) {
        log.info("[Kafka Consumer] Received message : {}", message);
    }

}
