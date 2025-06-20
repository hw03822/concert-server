package kr.hhplus.be.server.user.service;

import kr.hhplus.be.server.point.domain.BalanceHistory;
import kr.hhplus.be.server.point.repository.BalanceHistoryJpaRepository;
import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.dto.UserPointResponseDto;
import kr.hhplus.be.server.user.repository.UserJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private BalanceHistoryJpaRepository balanceHistoryJpaRepository;

    @InjectMocks
    private UserService userService;

    private static final String USER_ID = "user1";
    private static final Long MAX_BALANCE = 100_000_000L;

    @Test
    @DisplayName("신규 사용자의 포인트 충전이 정상적으로 이루어져야 한다")
    void chargePointForNewUser() {
        // given
        Long chargeAmount = 10_000L;
        when(userJpaRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(userJpaRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceHistoryJpaRepository.save(any(BalanceHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserPointResponseDto result = userService.chargePoint(USER_ID, chargeAmount);

        // then
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getBalance()).isEqualTo(chargeAmount);

        // 검증 : 사용자 저장됨
        verify(userJpaRepository).findByUserId(USER_ID);
        verify(userJpaRepository).save(any(User.class));

        // 검증 : 거래 내역 저장됨
        verify(balanceHistoryJpaRepository).save(argThat(history -> 
            history.getUserId().equals(USER_ID) &&
            history.getType().equals(BalanceHistory.TransactionType.CHARGE) &&
            history.getAmount().equals(chargeAmount) &&
            history.getCurrentBalance().equals(chargeAmount)
        ));
    }

    @Test
    @DisplayName("기존 사용자의 포인트 충전이 정상적으로 이루어져야 한다")
    void chargePointForExistingUser() {
        // given
        Long initialBalance = 20_000L;
        Long chargeAmount = 30_000L;
        User existingUser = new User(USER_ID, initialBalance);
        
        when(userJpaRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existingUser));
        when(userJpaRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceHistoryJpaRepository.save(any(BalanceHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserPointResponseDto result = userService.chargePoint(USER_ID, chargeAmount);

        // then
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getBalance()).isEqualTo(initialBalance + chargeAmount);

        // 검증 : 사용자 저장됨
        verify(userJpaRepository).findByUserId(USER_ID);
        verify(userJpaRepository).save(any(User.class));

        // 검증 : 거래 내역 저장됨
        verify(balanceHistoryJpaRepository).save(argThat(history ->
            history.getUserId().equals(USER_ID) &&
            history.getType().equals(BalanceHistory.TransactionType.CHARGE) &&
            history.getAmount().equals(chargeAmount) &&
            history.getCurrentBalance().equals(initialBalance + chargeAmount)
        ));
    }

    @Test
    @DisplayName("포인트 잔액 조회가 정상적으로 이루어져야 한다")
    void getBalance() {
        // given
        Long initialBalance = 50_000L;
        User user = new User(USER_ID, initialBalance);
        when(userJpaRepository.findByUserId(USER_ID)).thenReturn(Optional.of(user));

        // when
        UserPointResponseDto balance = userService.getBalance(USER_ID);

        // then
        assertThat(balance.getBalance()).isEqualTo(initialBalance);
        verify(userJpaRepository).findByUserId(USER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 포인트 조회 시 예외가 발생해야 한다")
    void getBalanceForNonExistentUser() {
        // given
        when(userJpaRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getBalance(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("사용자의 포인트 정보가 존재하지 않습니다.");
        verify(userJpaRepository).findByUserId(USER_ID);
    }
}
