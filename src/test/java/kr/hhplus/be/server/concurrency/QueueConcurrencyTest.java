package kr.hhplus.be.server.concurrency;

import kr.hhplus.be.server.common.RedisKeyUtils;
import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.queue.service.QueueService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class QueueConcurrencyTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 활성 사용자 최대수
    @Value("${queue.max-active-users:50}")
    private int maxActiveUsers;

    @Test
    @DisplayName("동시에 여러명이 대기열 토큰 발급 요청 시 최대 활성자 수를 넘지 않는다.")
    void concurrentTokenIssuanceTest() throws InterruptedException {
        // Given
        int concurrentUsers = maxActiveUsers + 10; // 최대 활성 사용자 수보다 10명 더 많은 사용자
        int threadPoolSize = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(concurrentUsers);

        AtomicInteger activeTokenCount = new AtomicInteger(0);
        AtomicInteger waitingTokenCount = new AtomicInteger(0);
        List<CompletableFuture<QueueToken>> futures = new ArrayList<>();

        // When
        for (int i = 0; i < concurrentUsers; i++) {
            final String userId = "user-" + i;

            CompletableFuture<QueueToken> future = CompletableFuture.supplyAsync(() -> {
                try {
                    QueueToken token = queueService.issueTokenWithLock(userId);

                    if (token.getStatus() == QueueToken.QueueStatus.ACTIVE) {
                        activeTokenCount.incrementAndGet();
                    } else if (token.getStatus() == QueueToken.QueueStatus.WAITING) {
                        waitingTokenCount.incrementAndGet();
                    }

                    return token;
                } catch (Exception e) {
                    throw new RuntimeException("[QueueConcurrencyTest] 토큰 발급 실패: " + userId, e);
                } finally {
                    latch.countDown();
                }
            }, executorService);

            futures.add(future);
        }

        // 모든 요청이 완료될 때까지 대기
        latch.await();

        // Then
        Long activeUserCount = redisTemplate.opsForSet().size(RedisKeyUtils.activeQueueKey());

        assertNotNull(activeUserCount);
        assertTrue(activeUserCount <= maxActiveUsers, "[QueueConcurrencyTest] 활성 사용자 수가 최댓값을 초과함");

        assertThat(activeTokenCount.get()).isLessThanOrEqualTo(maxActiveUsers);
        assertThat(activeTokenCount.get() + waitingTokenCount.get()).isEqualTo(concurrentUsers);

        // 모든 토큰이 정상적으로 발급되었는지 확인
        for (CompletableFuture<QueueToken> future : futures) {
            assertThat(future.isCompletedExceptionally()).isFalse();
        }

        executorService.shutdown();
    }

    @Test
    public void redis_정상작동_확인() {
        System.out.println("[QueueConcurrencyTest] Redis 연결 테스트");

        // given
        String testKey = "test:connection:" + System.currentTimeMillis();
        String testValue = "test-value";
        String result = "";

        try {

            redisTemplate.opsForValue().set(testKey, testValue);

            // when
            result = redisTemplate.opsForValue().get(testKey).toString();

        } catch (Exception e) {
            System.err.println("[QueueConcurrencyTest] Redis 연결 실패: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // then
        assertEquals(testValue, result);

        System.out.println("[QueueConcurrencyTest] Redis 연결 성공");
    }


}
