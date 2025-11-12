package kr.hhplus.be.server.external.kafka.test;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class kafkaTestProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * kafka producer 테스트
     * @param topic kafka 토픽
     * @param message 발행할 메시지
     */
    public void sendMessage(String topic, String message) {
        kafkaTemplate.send(topic, message);
    }
}
