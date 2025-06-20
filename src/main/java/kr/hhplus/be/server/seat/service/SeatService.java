package kr.hhplus.be.server.seat.service;

import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.dto.SeatResponseDto;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 좌석 관련 비즈니스 로직을 처리하는 서비스
 * 
 * @author hhplus
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatService {

    private final SeatJpaRepository seatJpaRepository;

    /**
     * 콘서트 모든 좌석 정보 조회
     *
     * @param concertId 조회할 공연의 ID
     * @return 해당 공연의 모든 좌석 정보 목록
     */
    public List<SeatResponseDto> getSeatsByConcertId(Long concertId) {
        List<Seat> seats = seatJpaRepository.findByConcertId(concertId);
        return seats.stream()
                .map(SeatResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 콘서트 좌석 정보 생성
     *
     * @param concertId 생성할 콘서트의 ID
     */
    public void setupSeatsByConcertId(Long concertId) {
        List<Seat> seats = new ArrayList<>();
        int totalSeats = 50;

        for (int seatNumber = 1; seatNumber <= totalSeats; seatNumber++) {
            Seat seat = new Seat(concertId, seatNumber, 50000);
            seats.add(seat);
        }

        seatJpaRepository.saveAll(seats);
    }

}
