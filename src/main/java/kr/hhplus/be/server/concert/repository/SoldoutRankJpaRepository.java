package kr.hhplus.be.server.concert.repository;

import kr.hhplus.be.server.concert.domain.SoldoutRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoldoutRankJpaRepository extends JpaRepository<SoldoutRank, Long> {
}
