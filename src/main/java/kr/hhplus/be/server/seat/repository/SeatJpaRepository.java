package kr.hhplus.be.server.seat.repository;

import io.lettuce.core.dynamic.annotation.Param;
import kr.hhplus.be.server.seat.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * 콘서트 ID로 이용가능한 좌석 리스트를 조회합니다.
     *
     * @param concertId 콘서트 ID
     * @return 이용가능한 좌석 리스트
     */
    @Query("SELECT s FROM Seat s WHERE s.concertId = :concertId AND s.status = 'AVAILABLE'")
    List<Seat> findAvailableSeatsByConcertId(@Param("concertId") Long concertId);

    /**
     * 콘서트 ID 와 좌석 상태가 RESERVED 가 아닌 좌석의 개수를 반환한다.
     */
    long countByConcertIdAndStatusNot(Long concertId, Seat.SeatStatus status);
}
