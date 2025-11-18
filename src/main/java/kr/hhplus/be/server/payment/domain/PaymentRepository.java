package kr.hhplus.be.server.payment.domain;

import io.lettuce.core.dynamic.annotation.Param;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(String paymentId);
    int cancelIfCompleted(@Param("paymentId") String paymentId);
}
