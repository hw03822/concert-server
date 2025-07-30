package kr.hhplus.be.server.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class kafkaIntegrationTest {

    @Autowired
    private kafkaTestProducer producer;

    @Test
    @DisplayName("TEST : kafka 메세지 전송 및 수신")
    void kafkaMessageSendAndReceive() throws InterruptedException {
        // given
        String topic = "test-topic";
        String message = "Test ";

        // when
        producer.sendMessage(topic, message);

        // then
        Thread.sleep(2000); // consumer 로그 수신 대기
    }

}