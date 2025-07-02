package kr.hhplus.be.server.payment.application.output;

import kr.hhplus.be.server.payment.domain.Payment;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PaymentResult {
    private final String paymentId;
    private final String reservationId;
    private final String userId;
    private final Long price;
    private final String status;
    private final LocalDateTime paidAt;

    public PaymentResult(Payment payment) {
        this.paymentId = payment.getPaymentId();
        this.reservationId = payment.getReservationId();
        this.userId = payment.getUserId();
        this.price = payment.getPrice();
        this.status = payment.getStatus().name();
        this.paidAt = payment.getCreatedAt();
    }
}
