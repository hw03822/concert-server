package kr.hhplus.be.server.queue.service;

import kr.hhplus.be.server.common.lock.RedisDistributedLock;
import kr.hhplus.be.server.queue.domain.QueueToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class QueueServiceTest {
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisDistributedLock redisDistributedLock;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    private QueueService queueService;


    @BeforeEach
    void setUp() {
        // RedisTemplate 의 Operations Mock 설정
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        queueService = new QueueService(redisTemplate, redisDistributedLock);

        //설정값 주입
        ReflectionTestUtils.setField(queueService, "maxActiveUsers", 100);
        ReflectionTestUtils.setField(queueService, "tokenExpireMinutes", 30);
        ReflectionTestUtils.setField(queueService, "waitTimePerUser", 20);
        ReflectionTestUtils.setField(queueService, "lockTimeoutSeconds", 5);
    }

    @Test
    @DisplayName("활성 사용자가 최대치 미만일 때 즉시 활성화된 토큰을 발급한다.")
    void issueToken_WhenActiveUsersLessThanMax_ShouldIssueActiveTokenWithLock() {
        //given
        String userId = "user-123";

        // 기존 토큰 없음 Mock
        when(valueOperations.get(startsWith("queue:user:token:"))).thenReturn(null);

        // 분산 락 획득 성공 Mock (ture 인 경우 락 획득)
        given(redisDistributedLock.tryLockWithRetry(anyString(), anyString(), anyLong())).willReturn(true);

        // 활성 사용자 목록이 비어있음 (만료된 사용자 정리용)
        when(setOperations.members("queue:active")).thenReturn(Collections.emptySet());

        // 현재 활성 사용자 수 50 - 최대치 미만
        when(setOperations.size("queue:active")).thenReturn(50L);

        // 활성 사용자 대기열에 추가 성공
        when(setOperations.add("queue:active", userId)).thenReturn(1L);

        //when
        // 토큰 발급 요청
        QueueToken result = queueService.issueTokenWithLock(userId);

        //then
        // 사용자 ID 검증 통과
        assertThat(result.getUserId()).isEqualTo(userId);

        // 토큰 상태 검증 통과
        assertThat(result.getStatus()).isEqualTo(QueueToken.QueueStatus.ACTIVE);

        // 대기 순서 검증 통과
        assertThat(result.getQueuePosition()).isEqualTo(0L);

        // 예상 대기 시간 검증 통과
        assertThat(result.getEstimatedWaitTimeMinutes()).isEqualTo(0);

        // 토큰 생성 검증 통과
        assertThat(result.getToken()).isNotNull();

        // Redis 호출 검증
        verify(setOperations).add(eq("queue:active"), eq(userId));
        // 토큰 저장 + 사용자-토큰 매핑 + 개별 활성 키 = 3개 (TTL 30분)
        verify(valueOperations, times(3)).set(anyString(), any(), eq(30L), eq(TimeUnit.MINUTES));

    }

    @Test
    @DisplayName("활성 사용자가 최대치일 때 대기열에 추가된 대기 토큰을 발급한다.")
    void issueToken_WhenActiveUsersAtMax_ShouldIssueWaitingTokenWithLock() {
        //given
        String userId = "user-456";

        // 기존 토큰 없음 Mock
        when(valueOperations.get(startsWith("queue:user:token:"))).thenReturn(null);

        // 분산 락 획득 성공 Mock (ture 인 경우 락 획득)
        given(redisDistributedLock.tryLockWithRetry(anyString(), anyString(), anyLong())).willReturn(true);

        // 활성 사용자 목록이 비어있음 (만료된 사용자 정리용)
        when(setOperations.members("queue:active")).thenReturn(Collections.emptySet());

        // 현재 활성 사용자 수 최대치
        when(setOperations.size("queue:active")).thenReturn(100L);
        // Queue 대기 순서 10번째
        when(zSetOperations.rank("queue:waiting", userId)).thenReturn(9L); // 0부터 시작

        //when
        QueueToken result = queueService.issueTokenWithLock(userId);

        //then
        // 사용자 ID 검증 통과
        assertThat(result.getUserId()).isEqualTo(userId);

        // 토큰 상태 검증 통과
        assertThat(result.getStatus()).isEqualTo(QueueToken.QueueStatus.WAITING);

        // 대기 순서 검증 통과
        assertThat(result.getQueuePosition()).isEqualTo(10L);

        // 예상 대기 시간 검증 통과
        assertThat(result.getEstimatedWaitTimeMinutes()).isEqualTo(3); // 20*10/60 = 3분

        // 토큰 생성 검증 통과
        assertThat(result.getToken()).isNotNull();

        // Redis 호출 검증
        verify(zSetOperations).add(eq("queue:waiting"), eq(userId), anyDouble());
        // 토큰 저장 + 사용자-토큰 매핑 = 2개 (TTL 30분)
        verify(valueOperations, times(2)).set(anyString(), any(), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("유효한 토큰으로 대기열 상태를 조회한다.")
    void getQueueStatus_ValidToken_ShouldReturnTokenInfo() {
        //given
        String token = "valid-token-123";
        String userId = "user-123";

        QueueToken mockToken = new QueueToken(
                token,
                userId,
                5L,
                2,
                QueueToken.QueueStatus.WAITING,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now().plusMinutes(30)
        );

        when(valueOperations.get("queue:token:" + token)).thenReturn(mockToken);

        when(zSetOperations.rank("queue:waiting", userId)).thenReturn(4L); // 5번째 순서

        //when
        QueueToken result = queueService.getQueueStatus(token);

        //then
        assertThat(result.getToken()).isEqualTo(token);
        assertThat(result.getStatus()).isEqualTo(QueueToken.QueueStatus.WAITING);
        assertThat(result.getQueuePosition()).isEqualTo(5L); // 4L + 1 = 5L

        verify(valueOperations).get("queue:token:" + token);
        verify(zSetOperations).rank("queue:waiting", userId);
    }

    @Test
    @DisplayName("유효하지 않는 토큰 조회 시 예외를 발생시킨다")
    void getQueueStatus_NonExistingToken_ShouldThrowException() {
        //given
        String nonExistingToken = "non-existing-token";

        when(valueOperations.get("queue:token:" + nonExistingToken)).thenReturn(null);

        //when & then
        assertThatThrownBy(() -> queueService.getQueueStatus(nonExistingToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("유효하지 않은 토큰입니다.");

        // Redis 호출 검증
        verify(valueOperations).get("queue:token:" + nonExistingToken);
    }

    @Test
    @DisplayName("활성 토큰 유효성 검증이 성공한다.")
    void validateActiveToken_validActiveToken_ShouldReturnTrue() {
        //given
        String activeToken = "active-token-123";
        QueueToken mockActiveToken = new QueueToken(
                activeToken,
                "user-123",
                0L,
                0,
                QueueToken.QueueStatus.ACTIVE,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(20)
        );

        when(valueOperations.get("queue:token:" + activeToken)).thenReturn(mockActiveToken);

        //when
        boolean result = queueService.validateActiveToken(activeToken);

        //then
        // 활성 토큰 유효성 검증 통과
        assertThat(result).isTrue();

        // 검증:Redis 호출 검증
        verify(valueOperations).get("queue:token:"+activeToken);
    }


}