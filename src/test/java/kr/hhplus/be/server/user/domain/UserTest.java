package kr.hhplus.be.server.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Long USER_ID = 1L;
    private static final Long MAX_BALANCE = 100_000_000L;

    @Test
    @DisplayName("신규 사용자의 포인트 충전이 정상적으로 이루어져야 한다")
    void chargePointForNewUser() {
        // given
        User user = new User(USER_ID, 0L);
        Long chargeAmount = 10_000L;

        // when
        user.charge(chargeAmount);

        // then
        assertThat(user.getBalance()).isEqualTo(chargeAmount);
        assertThat(user.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("기존 사용자의 포인트 충전이 정상적으로 이루어져야 한다")
    void chargePointForExistingUser() {
        // given
        Long initialBalance = 20_000L;
        User user = new User(USER_ID, initialBalance);
        Long chargeAmount = 30_000L;

        // when
        user.charge(chargeAmount);

        // then
        assertThat(user.getBalance()).isEqualTo(initialBalance + chargeAmount);
        assertThat(user.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("포인트 잔액 조회가 정상적으로 이루어져야 한다")
    void getBalance() {
        // given
        Long initialBalance = 50_000L;
        User user = new User(USER_ID, initialBalance);

        // when
        Long balance = user.getBalance();

        // then
        assertThat(balance).isEqualTo(initialBalance);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -1000})
    @DisplayName("0 이하의 금액으로 충전 시 예외가 발생해야 한다")
    void chargeWithInvalidAmount(Long invalidAmount) {
        // given
        User user = new User(USER_ID, 0L);

        // when & then
        assertThatThrownBy(() -> user.charge(invalidAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("충전 금액은 0원보다 커야 합니다.");
    }

    @Test
    @DisplayName("최대 금액을 초과하는 충전 시 예외가 발생해야 한다")
    void chargeExceedingMaxBalance() {
        // given
        User user = new User(USER_ID, MAX_BALANCE - 1000L);
        Long chargeAmount = 2000L;

        // when & then
        assertThatThrownBy(() -> user.charge(chargeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("충전 후 잔액이 100,000,000원 이상이 될 수 없습니다.");
    }

    @Test
    @DisplayName("사용자 ID가 null이면 예외가 발생해야 한다")
    void validateUserId_null() {
        assertThatThrownBy(() -> new User(null, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 필수입니다.");
    }

    @Test
    @DisplayName("충분한 잔액이 있을 때 차감이 성공한다")
    void whenDeductWithSufficientBalance_ThenShouldSucceed() {
        // given
        User user = new User(USER_ID, 2000L);
        Long deductAmount = 1000L;

        // when
        user.use(deductAmount);

        // then
        assertThat(user.getBalance()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("잔액 부족 시 차감이 실패한다")
    void whenDeductWithInsufficientBalance_ThenShouldThrowException() {
        // given
        User user = new User(USER_ID, 30000L);
        Long deductAmount = 50000L;

        // when & then
        assertThatThrownBy(() -> user.use(deductAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잔액이 부족합니다");
    }

    @Test
    @DisplayName("잔액 충분 여부를 정확히 판단한다")
    void whenCheckBalance_ThenShouldReturnCorrectResult() {
        // given
        User user = new User(USER_ID, 50000L);

        // when & then
        assertThat(user.hasEnoughBalance(30000L)).isTrue();
        assertThat(user.hasEnoughBalance(50000L)).isTrue();
        assertThat(user.hasEnoughBalance(60000L)).isFalse();
    }

}
