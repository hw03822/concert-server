package kr.hhplus.be.server.payment.application;

import kr.hhplus.be.server.payment.application.input.PaymentCommand;
import kr.hhplus.be.server.payment.application.output.PaymentResult;
import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.domain.PaymentRepository;
import kr.hhplus.be.server.point.domain.BalanceHistory;
import kr.hhplus.be.server.point.repository.BalanceHistoryJpaRepository;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.repository.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private SeatJpaRepository seatJpaRepository;

    @Mock
    private UserJpaRepository userJpaRepository;

    @Mock
    private BalanceHistoryJpaRepository balanceHistoryJpaRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Reservation reservation;
    private User user;
    private PaymentCommand command;
    private Seat seat;

    private static final String RES_ID = "res-123";
    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        reservation = new Reservation(USER_ID, 1L, 1L, LocalDateTime.now().plusMinutes(5), 100000, 20);
        user = new User(USER_ID, 120000L);
        command = new PaymentCommand(RES_ID, USER_ID);
        seat = new Seat(1L, 1L, 15, 100000);
        seat.assign(LocalDateTime.now().plusMinutes(5));
    }

    @Test
    @DisplayName("정상적인 결제 처리가 성공한다.")
    void whenProcessPayment_ThenShouldSucceed() {
        //given
        given(reservationRepository.findById(RES_ID)).willReturn(Optional.of(reservation));
        given(userJpaRepository.deductBalanceWithCondition(USER_ID, 100000L)).willReturn(1);
        given(userJpaRepository.findByUserId(USER_ID)).willReturn(Optional.of(user));
        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(seatJpaRepository.findById(1L)).willReturn(Optional.of(seat));
        given(seatJpaRepository.save(any(Seat.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(balanceHistoryJpaRepository.save(any(BalanceHistory.class))).willAnswer(invocation -> invocation.getArgument(0));

        //when
        PaymentResult result = paymentService.processPayment(command);

        //then
        assertThat(result).isNotNull();
        assertThat(result.getReservationId()).isEqualTo(RES_ID);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getPrice()).isEqualTo(100000L);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        // 검증:사용자 포인트 차감
        verify(userJpaRepository).deductBalanceWithCondition(
                argThat(u -> u.equals(USER_ID)),
                argThat(p -> p.equals(100000L))
        );
        // 검증:결제 정보 저장
        verify(paymentRepository).save(any(Payment.class));

        // 검증:예약 확정
        verify(reservationRepository).save(argThat(r ->
                r.getStatus() == Reservation.ReservationStatus.CONFIRMED));

        // 검증:좌석 확정
        verify(seatJpaRepository).save(argThat((s ->
                s.getStatus() == Seat.SeatStatus.RESERVED)));

        // 검증:포인트 거래 내역 저장
        verify(balanceHistoryJpaRepository).save(any(BalanceHistory.class));
    }

    @Test
    @DisplayName("존재하지 않는 예약에 대한 결제 시 예외가 발생한다.")
    void whenProcessPaymentForNonExistentReservation_ThenShouldThrowException() {
        //given
        given(reservationRepository.findById(RES_ID)).willReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예약 내역을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("잔액 부족 시 결제가 실패한다.")
    void whenProcessPaymentWithInsufficientBalance_ThenShouldThrowException() {
        //given
        User poorUser = new User(USER_ID, 30000L); // 잔액 부족
        given(reservationRepository.findById(RES_ID)).willReturn(Optional.of(reservation));
        given(userJpaRepository.deductBalanceWithCondition(USER_ID, 100000L)).willReturn(0);

        //when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("잔액이 부족합니다.");

        // 검증:결제 정보가 저장되지 않음
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("만료된 예약에 대한 결제 시 예외가 발생한다.")
    void whenProcessPaymentForExpiredReservation_ThenShouldThrowException() {
        //given
        Reservation expiredReservation = new Reservation(
                "user-123", 1L, 1L, LocalDateTime.now().minusMinutes(1), 100000, 20
        );
        given(reservationRepository.findById(RES_ID)).willReturn(Optional.of(expiredReservation));

        //when & then
        assertThatThrownBy(() -> paymentService.processPayment(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("예약이 만료되었습니다.");
    }
}