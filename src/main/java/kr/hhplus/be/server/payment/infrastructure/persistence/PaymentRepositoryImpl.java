package kr.hhplus.be.server.payment.infrastructure.persistence;

import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return paymentJpaRepository.findById(paymentId);
    }

    /**
     * 결제 취소 상태 업데이트 (조건부 UPDATE - 동시성 제어)
     * @param paymentId 결제 ID
     * @return 업데이트된 row 수 (1:성공, 0:이미 취소됨 or 취소할 수 없는 상태 or 충돌)
     */
    @Override
    @Modifying
    @Query("UPDATE Payment p set p.status = 'CANCELLED'" +
            "WHERE p.paymentId = :paymentId and p.status = 'COMPLETED'")
    public int cancelIfCompleted(String paymentId) {
        return 0;
    }


}
