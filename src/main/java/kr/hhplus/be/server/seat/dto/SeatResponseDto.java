package kr.hhplus.be.server.seat.dto;

import kr.hhplus.be.server.seat.domain.Seat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SeatResponseDto {
    private final Long seatId;
    private final Long concertId;
    private final Integer seatNumber;
    private final Long price;
    private final String status;
    private final LocalDateTime reservedAt;

    @Builder
    public SeatResponseDto(Long seatId, Long concertId, Integer seatNumber,
                           Long price, String status, LocalDateTime reservedAt) {
        this.seatId = seatId;
        this.concertId = concertId;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = status;
        this.reservedAt = reservedAt;
    }

    public static SeatResponseDto from(Seat seat) {
        return SeatResponseDto.builder()
                .seatId(seat.getSeatId())
                .concertId(seat.getConcertId())
                .seatNumber(seat.getSeatNumber())
                .price(seat.getPrice())
                .status(seat.getStatus().name())
                .reservedAt(seat.getReservedAt())
                .build();
    }
}
