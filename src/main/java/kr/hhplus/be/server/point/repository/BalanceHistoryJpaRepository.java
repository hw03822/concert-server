package kr.hhplus.be.server.point.repository;

import kr.hhplus.be.server.point.domain.BalanceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BalanceHistoryJpaRepository extends JpaRepository<BalanceHistory, String> {
    List<BalanceHistory> findByUserIdOrderByCreatedAtDesc(String userId);
}
