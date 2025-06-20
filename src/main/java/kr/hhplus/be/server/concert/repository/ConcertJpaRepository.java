package kr.hhplus.be.server.concert.repository;

import kr.hhplus.be.server.concert.domain.Concert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcertJpaRepository extends JpaRepository<Concert, Long> {
}
