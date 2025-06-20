package kr.hhplus.be.server.seat.repository;

import kr.hhplus.be.server.seat.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatJpaRepository extends JpaRepository<Seat, Long> {
    
    /**
     * 콘서트 ID로 좌석 목록을 조회합니다.
     *
     * @param concertId 콘서트 ID
     * @return 좌석 목록
     */
    List<Seat> findByConcertId(Long concertId);

    /**
     * 콘서트 ID와 좌석 번호로 좌석을 조회합니다.
     *
     * @param concertId 콘서트 ID
     * @param seatNumber 좌석 번호
     * @return 좌석 정보
     */
    Seat findByConcertIdAndSeatNumber(Long concertId, Integer seatNumber);
}
