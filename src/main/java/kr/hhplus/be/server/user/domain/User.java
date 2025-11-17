package kr.hhplus.be.server.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final Long MAX_BALANCE = 100_000_000L;

    @Id
    private String userId;

    @Column(nullable = false)
    private Long balance;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    //생성자
    public User(String userId, Long balance) {
        validateUserId(userId);
        validateInitialBalance(balance);
        this.userId = userId;
        this.balance = balance;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void charge(Long amount) {
        validateChargeAmount(amount);
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void use(Long amount) {
        validateUseAmount(amount);
        this.balance -= amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void refund(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("환불 포인트는 0보다 커야 합니다.");
        }
        this.balance += amount;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasEnoughBalance(Long amount) {
        return this.balance >= amount;
    }

    private void validateUserId(String userId) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
    }

    private void validateInitialBalance(Long balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("충전 금액은 0원보다 커야 합니다.");
        }
        if (balance > MAX_BALANCE) {
            throw new IllegalArgumentException("충전 금액이 100,000,000원 이상이 될 수 없습니다.");
        }
    }

    private void validateChargeAmount(Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전 금액은 0원보다 커야 합니다.");
        }
        if (this.balance + amount > MAX_BALANCE) {
            throw new IllegalArgumentException("충전 후 잔액이 100,000,000원 이상이 될 수 없습니다.");
        }
    }

    private void validateUseAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
        }
        if (!hasEnoughBalance(amount)) {
            throw new IllegalArgumentException(
                    String.format("잔액이 부족합니다. 현재 잔액: %d원, 필요 금액: %d원", this.balance, amount)
            );
        }
    }

    public Long getBalance() {
        if (this.userId == null) {
            throw new IllegalStateException("사용자 정보가 존재하지 않습니다.");
        }
        return this.balance;
    }
}
