package kr.hhplus.be.server.reservation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Reservation {
    @Id
    private String reservationId;
    private String userId;
    private Long concertId;
    private Long seatId;
    private ReservationStatus status;
    private LocalDateTime confirmedAt;
    private LocalDateTime expiredAt;
    private String concertTitle;
    private LocalDateTime concertAt;
    private Integer price;
    private Integer seatNum;

    public enum ReservationStatus {
        TEMPORARILY_ASSIGNED, CONFIRMED, CANCELLED, EXPIRED
    }

    public Reservation(String userId, Long concertId, Long seatId, LocalDateTime expiredAt, Integer price, Integer seatNum) {
        this.reservationId = UUID.randomUUID().toString();
        this.userId = userId;
        this.concertId = concertId;
        this.seatId = seatId;
        this.status = ReservationStatus.TEMPORARILY_ASSIGNED;
        this.expiredAt = expiredAt;
        this.price = price;
        this.seatNum = seatNum;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiredAt);
    }

    public void confirm(LocalDateTime confirmedAt) {
        if(this.status != ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("임시 배정 상태가 아닙니다.");
        }
        if(isExpired()) {
            throw new IllegalStateException("예약이 만료되었습니다.");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public void cancel() {
        if(this.status != ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("취소할 수 없는 상태입니다.");
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public void expire() {
        if(this.status != ReservationStatus.TEMPORARILY_ASSIGNED) {
            throw new IllegalStateException("만료 처리할 수 없는 상태입니다.");
        }
        if(isExpired()) {
            this.status = ReservationStatus.EXPIRED;
        }
    }

    public long getRemainingTimeSeconds() {
        if(isExpired()) {
            return 0;
        }
        return Duration.between(LocalDateTime.now(), this.expiredAt).getSeconds();
    }

}
