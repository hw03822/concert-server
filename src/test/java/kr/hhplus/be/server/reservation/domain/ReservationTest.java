package kr.hhplus.be.server.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class ReservationTest {

    private static final String USER_ID = "user-123";
    private static final Long CONCERT_ID = 1L;
    private static final Long SEAT_ID = 1L;
    private static final Long PRICE = 50000L;
    private static final Integer SEAT_NUMBER = 20;
    private static final LocalDateTime EXPIRED_AT = LocalDateTime.now().plusMinutes(5);

    @Test
    @DisplayName("예약 생성 시 기본 상태는 임시 배정이어야 한다.")
    void whenCreateReservation_ThenStatusShouldBeTemporarilyAssigned() {
        //given

        //when
        Reservation reservation = new Reservation(USER_ID, CONCERT_ID, SEAT_ID, EXPIRED_AT, PRICE, SEAT_NUMBER);

        //then
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.TEMPORARILY_ASSIGNED);
        assertThat(reservation.getUserId()).isEqualTo(USER_ID);
        assertThat(reservation.getConcertId()).isEqualTo(CONCERT_ID);
        assertThat(reservation.getSeatId()).isEqualTo(SEAT_ID);
        assertThat(reservation.getPrice()).isEqualTo(PRICE);
        assertThat(reservation.getExpiredAt()).isEqualTo(EXPIRED_AT);
        assertThat(reservation.getReservationId()).isNotNull();
    }

    @Test
    @DisplayName("임시 배정 상태의 예약을 확정하면 CONFIRMED로 변경된다.")
    void whenConfirmTemporarilyAssignedReservation_ThenStatusShouldBeConfirmed() {
        //given
        Reservation reservation = new Reservation(USER_ID, CONCERT_ID, SEAT_ID, EXPIRED_AT, PRICE, SEAT_NUMBER);
        LocalDateTime confirmedAt = LocalDateTime.now();

        //when
        reservation.confirm(confirmedAt);

        //then
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(confirmedAt);
    }

    @Test
    @DisplayName("임시 배정 상태의 예약을 취소하면 CANCELLED로 변경된다.")
    void whenCancelTemporarilyAssignedReservation_ThenStatusShouldBeCancelled() {
        //given
        Reservation reservation = new Reservation(USER_ID, CONCERT_ID, SEAT_ID, EXPIRED_AT, PRICE, SEAT_NUMBER);

        //when
        reservation.cancel();

        //then
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("임시 배정 상태에 만료 시간이 지나면 EXPIRED로 변경된다.")
    void whenExpireTemporarilyAssignedReservation_ThenStatusShouldBeExpired() {
        //given
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(6);
        Reservation reservation = new Reservation(USER_ID, CONCERT_ID, SEAT_ID, pastTime, PRICE, SEAT_NUMBER);

        //when
        reservation.expire();

        //then
        assertThat(reservation.getStatus()).isEqualTo(Reservation.ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("만료된 예약을 확정하려고 하면 예외가 발생한다.")
    void whenConfirmExpiredReservation_ThenShouldThrowException() {
        //given
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        Reservation reservation = new Reservation(USER_ID, CONCERT_ID, SEAT_ID, pastTime, PRICE, SEAT_NUMBER);

        //when & then
        assertThatThrownBy(()->reservation.confirm(LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예약이 만료되었습니다.");

    }

    @Test
    @DisplayName("남은 시간을 계산한다")
    void whenCalculateRemainingTime_ThenShouldReturnCorrectSeconds() {
        //given
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(5);
        Reservation reservation = new Reservation(USER_ID, CONCERT_ID, SEAT_ID, expiredAt, PRICE, SEAT_NUMBER);

        //when
        long remainingTime = reservation.getRemainingTimeSeconds();

        //then
        assertThat(remainingTime).isBetween(295L, 300L); // 약 5분 차이 (5초 오차 허용)
    }

    @Test
    @DisplayName("만료된 예약의 남은 시간은 0이다.")
    void whenReservationExpired_ThenRemainingTimeShouldBeZero() {
        //given
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        Reservation reservation = new Reservation(USER_ID, CONCERT_ID, SEAT_ID, pastTime, PRICE, SEAT_NUMBER);

        //when
        long remainingTime = reservation.getRemainingTimeSeconds();

        //then
        assertThat(remainingTime).isZero();
        assertThat(reservation.isExpired()).isTrue();
    }

}