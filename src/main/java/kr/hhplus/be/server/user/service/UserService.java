package kr.hhplus.be.server.user.service;

import kr.hhplus.be.server.point.domain.BalanceHistory;
import kr.hhplus.be.server.point.repository.BalanceHistoryJpaRepository;
import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.dto.UserPointResponseDto;
import kr.hhplus.be.server.user.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserJpaRepository userJpaRepository;
    private final BalanceHistoryJpaRepository balanceHistoryJpaRepository;

    /**
     * 포인트를 충전한다.
     *
     * @param userId 사용자 ID
     * @param amount 금액
     * @return 포인트 저장
     */
    @Transactional
    public UserPointResponseDto chargePoint(String userId, Long amount) {
        // 사용자 조회
        User user = userJpaRepository.findByUserId(userId)
                .orElse(new User(userId, 0L));
        
        // 포인트 충전
        user.charge(amount);

        // 사용자 포인트 저장
        User savedUser = userJpaRepository.save(user);

        // 거래 내역 저장
        BalanceHistory history = BalanceHistory.charge(userId, amount, savedUser.getBalance());
        balanceHistoryJpaRepository.save(history);

        return new UserPointResponseDto(savedUser);
    }

    /**
     * 포인트를 잔액을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 포인트 반환
     */
    public UserPointResponseDto getBalance(String userId) {
        User user = userJpaRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("사용자의 포인트 정보가 존재하지 않습니다."));
        
        return new UserPointResponseDto(user);
    }
}
