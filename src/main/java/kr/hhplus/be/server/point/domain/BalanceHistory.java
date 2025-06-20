package kr.hhplus.be.server.point.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceHistory {
    @Id
    private String historyId;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long currentBalance;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        CHARGE, PAYMENT, REFUND
    }

    // 생성자
    public BalanceHistory(String userId, TransactionType type, Long amount, Long currentBalance) {
        this.historyId = UUID.randomUUID().toString();
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.currentBalance = currentBalance;
        this.createdAt = LocalDateTime.now();
    }

    // 정적 팩토리 메소드
    // 잔액 충전
    public static BalanceHistory charge(String userId, Long amount, Long currentBalance) {
        return new BalanceHistory(userId, TransactionType.CHARGE, amount, currentBalance);
    }

    // 결제
    public static BalanceHistory payment(String userId, Long amount, Long currentBalance) {
        return new BalanceHistory(userId, TransactionType.PAYMENT, amount, currentBalance);
    }

    // 환불
    public static BalanceHistory refund(String userId, Long amount, Long currentBalance) {
        return new BalanceHistory(userId, TransactionType.REFUND, amount, currentBalance);
    }

    
}

