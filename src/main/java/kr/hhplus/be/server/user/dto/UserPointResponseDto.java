package kr.hhplus.be.server.user.dto;

import kr.hhplus.be.server.user.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
public class UserPointResponseDto {
    private final String userId;
    private final Long balance;

    public UserPointResponseDto(User user) {
        this.userId = user.getUserId();
        this.balance = user.getBalance();
    }

}
