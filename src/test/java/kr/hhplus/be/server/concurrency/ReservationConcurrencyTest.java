package kr.hhplus.be.server.concurrency;

import kr.hhplus.be.server.common.lock.RedisDistributedLock;
import kr.hhplus.be.server.queue.service.QueueService;
import kr.hhplus.be.server.reservation.application.ReservationService;
import kr.hhplus.be.server.reservation.application.input.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.application.output.ReserveSeatResult;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class ReservationConcurrencyTest {

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private ReservationService reservationService;

    @MockitoBean
    private QueueService queueService;

    @MockitoBean
    private RedisDistributedLock redisDistributedLock;

    private static final int THREAD_COUNT = 5;
    private static final String VALID_TOKEN = "VALID_TOKEN";

    @BeforeEach
    void setUp() {
        when(queueService.validateActiveToken(anyString())).thenReturn(true);
        
        // 분산락이 항상 성공하도록 설정 (실제 동시성 테스트를 위해)
//        when(redisDistributedLock.tryLockWithRetry(anyString(), anyString(), anyLong()))
//            .thenReturn(true);
    }

}
