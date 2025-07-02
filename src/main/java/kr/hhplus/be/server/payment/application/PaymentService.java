package kr.hhplus.be.server.payment.application;

import jakarta.transaction.Transactional;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final SeatJpaRepository seatJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final BalanceHistoryJpaRepository balanceHistoryJpaRepository;

    /**
     * 결제 처리 기능
     * @param command 요청 정보
     * @return 결제 완료 정보
     */
    @Transactional
    public PaymentResult processPayment(PaymentCommand command) {
        // 1. 예약 내역 정보 확인
        Reservation reservation = reservationRepository.findById(command.getReservationId())
                .orElseThrow(() -> new IllegalStateException("예약 내역을 찾을 수 없습니다."));

        // 1-1. 예약이 유효한지 확인
        validateReservation(reservation, command.getUserId());

        // 2. 사용자 포인트 잔액 확인 (조건부 UPDATE 사용 - 동시성 제어)
        Long paymentPrice = reservation.getPrice().longValue();
        int updatedRows = userJpaRepository.deductBalanceWithCondition(command.getUserId(), paymentPrice);

        if(updatedRows == 0) { //1:성공, 0:잔액 부족 or 충돌
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        // 2-1. 포인트 차감 후 잔액 조회 (히스토리 내역 저장 시 사용)
        User user = userJpaRepository.findByUserId(command.getUserId())
                .orElseThrow(() -> new IllegalStateException("사용자 정보를 찾을 수 없습니다."));

        // 3. 결제 내역 생성 및 저장
        Payment payment = new Payment(
                command.getReservationId(),
                command.getUserId(),
                paymentPrice
        );
        paymentRepository.save(payment);

        // 4. 예약 확정
        reservation.confirm(LocalDateTime.now());
        reservationRepository.save(reservation);

        // 5. 좌석 확정
        Seat seat = seatJpaRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new IllegalStateException("좌석 정보를 찾을 수 없습니다."));
        seat.confirmReservation(LocalDateTime.now());
        seatJpaRepository.save(seat);

        // 6. 포인트 히스토리 저장
        BalanceHistory balanceHistory = BalanceHistory.payment(
                command.getUserId(),
                paymentPrice,
                user.getBalance()
        );
        balanceHistoryJpaRepository.save(balanceHistory);

        return new PaymentResult(payment);
    }

    /**
     * 결제 정보 조회
     * @param paymentId 결제 ID
     * @return 결제 정보
     */
    public PaymentResult getPaymentInfo(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("결제 내역을 찾을 수 없습니다."));

        return new PaymentResult(payment);
    }

    /**
     * 예약 유효성 검증 (결제시)
     * @param reservation 예약 정보
     * @param userId 로그인한 사용자 ID
     */
    private void validateReservation(Reservation reservation, String userId) {
        // 예약자 확인
        if(!reservation.getUserId().equals(userId)) {
            throw new IllegalStateException("예약자가 일치하지 않습니다.");
        }

        // 예약 상태 확인
        if(reservation.getStatus() != Reservation.ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("결제할 수 없는 예약 상태입니다.");
        }

        // 예약 만료 확인
        if(reservation.isExpired()) {
            throw new IllegalStateException("예약이 만료되었습니다. 다시 예약해주세요.");
        }
    }
}
