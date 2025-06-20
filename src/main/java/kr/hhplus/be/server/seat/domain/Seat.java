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

    private LocalDateTime reservedAt;

    public enum SeatStatus {
        AVAILABLE, TEMPORARILY_ASSIGNED, RESERVED
    }

    // 생성자
    public Seat(Long concertId, Integer seatNumber, Integer price) {
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    // 비즈니스 로직 메소드
    public void assign() {
        if (status == SeatStatus.RESERVED) {
            throw new IllegalStateException("이미 예약된 좌석입니다.");
        }
        this.status = SeatStatus.TEMPORARILY_ASSIGNED;
    }

    public void reserve() {
        if (status == SeatStatus.RESERVED) {
            throw new IllegalStateException("이미 예약된 좌석입니다.");
        }
        if (status != SeatStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("임시 배정되지 않은 좌석입니다.");
        }
        this.status = SeatStatus.RESERVED;
        this.reservedAt = LocalDateTime.now();
    }

    public void releaseAssign() {
        if (status != SeatStatus.TEMPORARILY_ASSIGNED) {
            // 임시 배정 상태에만 해제 가능
            throw new IllegalStateException("임시 배정 상태가 아닙니다.");
        }
        this.status = SeatStatus.AVAILABLE;
        this.reservedAt = null;
    }
}
