package kr.hhplus.be.server.seat.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatTest {

    private static final Long CONCERT_ID = 1L;
    private static final Integer SEAT_NUMBER = 1;
    private static final Integer PRICE = 50_000;

    @Test
    @DisplayName("좌석을 임시 배정할 수 있어야 한다")
    void assignSeat() {
        // given
        Seat seat = new Seat(CONCERT_ID, SEAT_NUMBER, PRICE);

        // when
        seat.assign();

        // then
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.TEMPORARILY_ASSIGNED);
        assertThat(seat.getReservedAt()).isNull();
    }

    @Test
    @DisplayName("임시 배정된 좌석을 예약할 수 있어야 한다")
    void reserveAssignedSeat() {
        // given
        Seat seat = new Seat(CONCERT_ID, SEAT_NUMBER, PRICE);
        seat.assign();

        // when
        seat.reserve();

        // then
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.RESERVED);
        assertThat(seat.getReservedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 예약된 좌석은 임시 배정할 수 없다")
    void cannotAssignReservedSeat() {
        // given
        Seat seat = new Seat(CONCERT_ID, SEAT_NUMBER, PRICE);
        seat.assign();
        seat.reserve();

        // when & then
        assertThatThrownBy(() -> seat.assign())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 예약된 좌석입니다.");
    }

    @Test
    @DisplayName("이미 예약된 좌석은 다시 예약할 수 없다")
    void cannotReserveReservedSeat() {
        // given
        Seat seat = new Seat(CONCERT_ID, SEAT_NUMBER, PRICE);
        seat.assign();
        seat.reserve();

        // when & then
        assertThatThrownBy(() -> seat.reserve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 예약된 좌석입니다.");
    }

    @Test
    @DisplayName("임시 배정되지 않은 좌석은 예약할 수 없다")
    void cannotReserveUnassignedSeat() {
        // given
        Seat seat = new Seat(CONCERT_ID, SEAT_NUMBER, PRICE);

        // when & then
        assertThatThrownBy(() -> seat.reserve())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("임시 배정되지 않은 좌석입니다.");
    }

    @Test
    @DisplayName("임시 배정을 해제하면 상태가 AVAILABLE로 변경된다.")
    void releaseAssignSeat() {
        // given
        Seat seat = new Seat(CONCERT_ID, SEAT_NUMBER, PRICE);
        seat.assign();

        // when
        seat.releaseAssign();

        // then
        assertThat(seat.getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);
        assertThat(seat.getReservedAt()).isNull();
    }

}