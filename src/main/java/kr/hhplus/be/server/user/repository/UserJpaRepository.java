package kr.hhplus.be.server.user.repository;

import kr.hhplus.be.server.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
