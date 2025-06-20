package kr.hhplus.be.server.queue.service;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class QueueServiceTest {
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

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

        queueService = new QueueService(redisTemplate);

        //설정값 주입
        ReflectionTestUtils.setField(queueService, "maxActiveUsers", 100);
        ReflectionTestUtils.setField(queueService, "tokenExpireMinutes", 30);
        ReflectionTestUtils.setField(queueService, "waitTimePerUser", 20);
        ReflectionTestUtils.setField(queueService, "lockTimeoutSeconds", 5);
    }

    @Test
    @DisplayName("활성 사용자가 최대치 미만일 때 즉시 활성화된 토큰을 발급한다.")
    void issueToken_WhenActiveUsersLessThanMax_ShouldIssueActiveToken() {
        //given
        String userId = "user-123";

        // 기존 토큰 없음 Mock
        when(valueOperations.get(startsWith("queue:user:token:"))).thenReturn(null);

        // 분산 락 획득 성공 Mock (ture 인 경우 락 획득)
        when(valueOperations.setIfAbsent(eq("queue:lock"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        // 활성 사용자 목록이 비어있음 (만료된 사용자 정리용)
        when(setOperations.members("queue:active")).thenReturn(Collections.emptySet());

        // 현재 활성 사용자 수 50 - 최대치 미만
        when(setOperations.size("queue:active")).thenReturn(50L);

        //when
        // 토큰 발급 요청
        QueueToken result = queueService.issueToken(userId);

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
        verify(valueOperations, times(2)).set(anyString(), any(), eq(30L), eq(TimeUnit.MINUTES));

    }

    @Test
    @DisplayName("활성 사용자가 최대치일 때 대기열에 추가된 대기 토큰을 발급한다.")
    void issueToken_WhenActiveUsersAtMax_ShouldIssueWaitingToken() {
        //given
        String userId = "user-456";

        // 기존 토큰 없음 Mock
        when(valueOperations.get(startsWith("queue:user:token:"))).thenReturn(null);

        // 분산 락 획득 성공 Mock (ture 인 경우 락 획득)
        when(valueOperations.setIfAbsent(eq("queue:lock"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        // 활성 사용자 목록이 비어있음 (만료된 사용자 정리용)
        when(setOperations.members("queue:active")).thenReturn(Collections.emptySet());

        // 현재 활성 사용자 수 최대치
        when(setOperations.size("queue:active")).thenReturn(100L);
        // Queue 대기 순서 10번째
        when(zSetOperations.rank("queue:waiting", userId)).thenReturn(9L); // 0부터 시작

        //when
        QueueToken result = queueService.issueToken(userId);

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
    @DisplayName("대기 중인 사용자 활성화 - 활성화 가능한 슬롯이 있을 때 대기열에서 사용자를 활성화한다.")
    void activateWaitingUsers_WhenSlotsAvailable_ShouldActivateUsers() {
        // given
        String userId1 = "user-1";
        String userId2 = "user-2";
        String token1 = "token-1";
        String token2 = "token-2";
        
        Set<Object> waitingUsers = new HashSet<>(Arrays.asList(userId1, userId2));
        
        // 락 획득 성공
        when(valueOperations.setIfAbsent(eq("queue:lock"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        
        // 활성 사용자 수 98 (2개 슬롯 사용 가능)
        when(setOperations.size("queue:active")).thenReturn(98L);
        
        // 대기열에서 사용자들 가져오기
        when(zSetOperations.range("queue:waiting", 0, 1)).thenReturn(waitingUsers);
        
        // 사용자 토큰 매핑
        when(valueOperations.get("queue:user:token:" + userId1)).thenReturn(token1);
        when(valueOperations.get("queue:user:token:" + userId2)).thenReturn(token2);
        
        // 기존 토큰 정보 (WAITING 상태)
        QueueToken waitingToken1 = new QueueToken(
                token1, userId1, 1L, 1, 
                QueueToken.QueueStatus.WAITING,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now().plusMinutes(30)
        );
        QueueToken waitingToken2 = new QueueToken(
                token2, userId2, 2L, 2, 
                QueueToken.QueueStatus.WAITING,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now().plusMinutes(30)
        );
        
        when(valueOperations.get("queue:token:" + token1)).thenReturn(waitingToken1);
        when(valueOperations.get("queue:token:" + token2)).thenReturn(waitingToken2);
        
        // when
        queueService.activateWaitingUsers();
        
        // then
        // 대기열에서 사용자 제거
        verify(zSetOperations).remove("queue:waiting", userId1);
        verify(zSetOperations).remove("queue:waiting", userId2);
        
        // 활성 사용자로 추가
        verify(setOperations).add("queue:active", userId1);
        verify(setOperations).add("queue:active", userId2);
        
        // 토큰 상태 업데이트 (ACTIVE로 변경)
        verify(valueOperations, times(2)).set(anyString(), any(QueueToken.class), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("대기 중인 사용자 활성화 - 활성화 가능한 슬롯이 없을 때 아무것도 하지 않는다.")
    void activateWaitingUsers_WhenNoSlotsAvailable_ShouldDoNothing() {
        // given
        // 락 획득 성공
        when(valueOperations.setIfAbsent(eq("queue:lock"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        
        // 활성 사용자 수 최대치 (슬롯 없음)
        when(setOperations.size("queue:active")).thenReturn(100L);
        
        // when
        queueService.activateWaitingUsers();
        
        // then
        // 대기열 조회하지 않음
        verify(zSetOperations, never()).range(anyString(), anyLong(), anyLong());
        verify(zSetOperations, never()).remove(anyString(), anyString());
        verify(setOperations, never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("대기 중인 사용자 활성화 - 대기열이 비어있을 때 아무것도 하지 않는다.")
    void activateWaitingUsers_WhenWaitingQueueEmpty_ShouldDoNothing() {
        // given
        // 락 획득 성공
        when(valueOperations.setIfAbsent(eq("queue:lock"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        
        // 활성 사용자 수 50 (50개 슬롯 사용 가능)
        when(setOperations.size("queue:active")).thenReturn(50L);
        
        // 대기열 비어있음
        when(zSetOperations.range("queue:waiting", 0, 49)).thenReturn(Collections.emptySet());
        
        // when
        queueService.activateWaitingUsers();
        
        // then
        // 대기열 조회는 했지만 제거나 추가 작업 없음
        verify(zSetOperations).range("queue:waiting", 0, 49);
        verify(zSetOperations, never()).remove(anyString(), anyString());
        verify(setOperations, never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("대기 중인 사용자 활성화 - 락 획득 실패 시 아무것도 하지 않는다.")
    void activateWaitingUsers_WhenLockAcquisitionFails_ShouldDoNothing() {
        // given
        // 락 획득 실패
        when(valueOperations.setIfAbsent(eq("queue:lock"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);
        
        // when
        queueService.activateWaitingUsers();
        
        // then
        // 아무 Redis 작업도 수행하지 않음
        verify(setOperations, never()).size(anyString());
        verify(zSetOperations, never()).range(anyString(), anyLong(), anyLong());
        verify(zSetOperations, never()).remove(anyString(), anyString());
        verify(setOperations, never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("대기 중인 사용자 활성화 - 사용자 토큰이 없을 때 해당 사용자는 건너뛴다.")
    void activateWaitingUsers_WhenUserTokenNotFound_ShouldSkipUser() {
        // given
        String userId1 = "user-1";
        String userId2 = "user-2";
        String token2 = "token-2";
        
        Set<Object> waitingUsers = new HashSet<>(Arrays.asList(userId1, userId2));
        
        // 락 획득 성공
        when(valueOperations.setIfAbsent(eq("queue:lock"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        
        // 활성 사용자 수 98 (2개 슬롯 사용 가능)
        when(setOperations.size("queue:active")).thenReturn(98L);
        
        // 대기열에서 사용자들 가져오기
        when(zSetOperations.range("queue:waiting", 0, 1)).thenReturn(waitingUsers);
        
        // 첫 번째 사용자는 토큰 없음, 두 번째 사용자는 토큰 있음
        when(valueOperations.get("queue:user:token:" + userId1)).thenReturn(null);
        when(valueOperations.get("queue:user:token:" + userId2)).thenReturn(token2);
        
        // 두 번째 사용자 토큰 정보
        QueueToken waitingToken2 = new QueueToken(
                token2, userId2, 2L, 2, 
                QueueToken.QueueStatus.WAITING,
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now().plusMinutes(30)
        );
        when(valueOperations.get("queue:token:" + token2)).thenReturn(waitingToken2);
        
        // when
        queueService.activateWaitingUsers();
        
        // then
        // 두 사용자 모두 대기열에서 제거
        verify(zSetOperations).remove("queue:waiting", userId1);
        verify(zSetOperations).remove("queue:waiting", userId2);
        
        // 두 번째 사용자만 활성 사용자로 추가
        verify(setOperations, never()).add("queue:active", userId1);
        verify(setOperations).add("queue:active", userId2);
        
        // 두 번째 사용자 토큰만 업데이트
        verify(valueOperations, times(1)).set(anyString(), any(QueueToken.class), eq(30L), eq(TimeUnit.MINUTES));
    }
}