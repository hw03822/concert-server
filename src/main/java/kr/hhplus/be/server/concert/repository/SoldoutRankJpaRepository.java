package kr.hhplus.be.server.concert.repository;

import io.lettuce.core.dynamic.annotation.Param;
import kr.hhplus.be.server.concert.domain.SoldoutRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SoldoutRankJpaRepository extends JpaRepository<SoldoutRank, Long> {

    /**
     * 상위 limit 개의 매진 랭킹 조회
     */
    @Query(value = "SELECT * FROM soldoutRank ORDER BY soldoutAt ASC LIMIT :limit", nativeQuery = true)
    List<SoldoutRank> findTopRankingWithLimit(@Param("limit") int limit);
}
