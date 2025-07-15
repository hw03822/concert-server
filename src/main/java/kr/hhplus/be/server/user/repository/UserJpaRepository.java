package kr.hhplus.be.server.user.repository;

import io.lettuce.core.dynamic.annotation.Param;
import kr.hhplus.be.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<User, Long> {
    
    /**
     * 사용자 ID로 포인트 정보를 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 포인트 정보 (Optional)
     */
    Optional<User> findByUserId(String userId);

    /**
     * 사용자 포인트 차감 업데이트 (조건부 UPDATE - 동시성 제어)
     * @param userId 사용자 ID
     * @param price 결제할 가격
     * @return 업데이트된 row 수 (1:성공, 0:잔액 부족 or 충돌)
     */
    @Modifying
    @Query("UPDATE User u SET u.balance = u.balance - :price" +
            " WHERE u.userId = :userId AND u.balance >= :price")
    int deductBalanceWithCondition(@Param("userId") String userId, @Param("price") Long price);
}
