package kr.hhplus.be.server.external.dataplatform;

import kr.hhplus.be.server.external.dataplatform.request.ReservatoinSendRequestDto;
import kr.hhplus.be.server.reservation.domain.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DataPlatformSender {

    private static final Logger log = LoggerFactory.getLogger(DataPlatformSender.class);

    /**
     * 수신된 예약 정보 이벤트를 받아 외부 DataPlatform 에 전송
     * @param reservation 예약 정보 이벤트
     */
    public void send(ReservatoinSendRequestDto reservation){
        // 1. mock API 호출
        log.info("DataPlatform 예약 정보 전송 : {}", reservation);
    }
}
