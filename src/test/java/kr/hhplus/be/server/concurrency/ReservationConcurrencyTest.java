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

    private static final int THREAD_COUNT = 2;
    private static final String VALID_TOKEN = "VALID_TOKEN";

    @BeforeEach
    void setUp() {
        when(queueService.validateActiveToken(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("동시에 여러명이 하나의 좌석 예약 요청 시 한 명만 성공한다")
    void onlyOneSuccessWhenMultipleUsersReserveSameSeat() throws Exception {
        // given
        Long concertId = 1L;
        Integer seatNumber = 1;

        Seat seat = new Seat(null, concertId, seatNumber, 50000);
        seatJpaRepository.save(seat);
        System.out.println("[ReservationConcurrencyTest] seat.status : " + seat.getStatus());

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT); //스레드 풀 생성
        CountDownLatch latch = new CountDownLatch(1); // 동시 시작을 위한 래치
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            final String userId = "user-" + i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    latch.await(); // 모두가 동시에 시작되도록 대기
                    try {
                        System.out.println("[ReservationConcurrencyTest] userId : " + userId);
                        ReserveSeatResult result = reservationService.reserveSeat(new ReserveSeatCommand(userId, concertId, seatNumber), VALID_TOKEN);
                        System.out.printf("[ReservationConcurrencyTest] reservationId : %s" , result.getReservationId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executorService));
        }

        // 대기 중인 스레드 모두 실행
        latch.countDown();

        // 모든 작업 완료 대기
        for (CompletableFuture<Void> future : futures) {
            future.get();
        }

        // then
        assertThat(successCount.get()).isEqualTo(1); // 한 명만 예약 성공
        assertThat(failureCount.get()).isEqualTo(THREAD_COUNT - 1);
    }
}
