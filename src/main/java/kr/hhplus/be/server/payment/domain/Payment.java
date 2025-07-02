package kr.hhplus.be.server.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    private String paymentId;

    @Column(nullable = false)
    private String reservationId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum PaymentStatus {
        COMPLETED, FAILED, CANCELLED
    }

    // 생성자
    public Payment(String reservationId, String userId, Long price) {
        this.paymentId = UUID.randomUUID().toString();
        this.reservationId = reservationId;
        this.userId = userId;
        this.price = price;
        this.status = PaymentStatus.COMPLETED; // 생성 즉시 완료 상태
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 결제 실패 처리
     */
    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    /**
     * 결제 취소 처리
     * COMPLETED 상태에서만 CANCELLED로 변경 가능
     */
    public void cancel() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("완료된 결제만 취소할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.CANCELLED;
    }

    /**
     * 결제가 완료 상태인지 확인
     */
    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    /**
     * 결제가 실패 상태인지 확인
     */
    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }

    /**
     * 결제가 환불 가능한 상태인지 확인 (완료된 결제만 환불 가능)
     */
    public boolean isRefundable() {
        return this.status == PaymentStatus.COMPLETED;
    }

}