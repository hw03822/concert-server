package kr.hhplus.be.server.seat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;

    @Column(nullable = false)
    private Long concertId;

    @Column(nullable = false)
    private Integer seatNumber;

    @Column(nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    private LocalDateTime assignedUntil;
    private LocalDateTime reservedAt;

    public enum SeatStatus {
        AVAILABLE, TEMPORARILY_ASSIGNED, RESERVED
    }

    // 생성자
    public Seat(Long seatId, Long concertId, Integer seatNumber, Integer price) {
        this.seatId = seatId;
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    // 비즈니스 로직 메소드
    public void assign(LocalDateTime expiredAt) {
        if (!isAvailable()) {
            throw new IllegalStateException("이미 예약된 좌석입니다.");
        }
        this.status = SeatStatus.TEMPORARILY_ASSIGNED;
        this.assignedUntil = expiredAt;
    }

    public void confirmReservation(LocalDateTime confirmedAt) {
        if (!isTemporarilyAssigned()) {
            throw new IllegalStateException("임시 배정되지 않은 좌석입니다.");
        }
        if (isExpired()) {
            throw new IllegalStateException("임시 배정 시간이 만료되었습니다.");
        }
        this.status = SeatStatus.RESERVED;
        this.reservedAt = confirmedAt;
    }

    public void releaseAssign() {
        if (!isTemporarilyAssigned()) {
            // 임시 배정 상태에만 해제 가능
            throw new IllegalStateException("임시 배정 상태가 아닙니다.");
        }
        this.status = SeatStatus.AVAILABLE;
        this.assignedUntil = null;
    }

    public boolean isAvailable() {
        return this.status == SeatStatus.AVAILABLE;
    }

    public boolean isTemporarilyAssigned() {
        return this.status == SeatStatus.TEMPORARILY_ASSIGNED;
    }

    public boolean isExpired() {
        if(!isTemporarilyAssigned()) {
            return false;
        }
        return this.assignedUntil != null && LocalDateTime.now().isAfter(this.assignedUntil);
    }
}
