package kr.hhplus.be.server.integration;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.repository.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

public class TestDataHelper {

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private SeatJpaRepository seatJpaRepository;

    @Autowired
    private ConcertJpaRepository concertJpaRepository;

    public User createUser() {
        User user = new User("user-123", 100000L);
        return userJpaRepository.save(user);
    }

    public Seat createSeat() {
        Seat seat = new Seat(20L, 1L, 20, 50000L);
        return seatJpaRepository.save(seat);
    }

    public Concert createConcert() {
        Concert concert = new Concert(1L, "흠뻑쇼", "싸이", LocalDateTime.now());
        return concertJpaRepository.save(concert);
    }

}
