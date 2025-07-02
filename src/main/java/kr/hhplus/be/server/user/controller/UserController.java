package kr.hhplus.be.server.user.controller;

import kr.hhplus.be.server.user.domain.User;
import kr.hhplus.be.server.user.dto.UserPointRequestDto;
import kr.hhplus.be.server.user.dto.UserPointResponseDto;
import kr.hhplus.be.server.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 포인트를 충전한다.
     *
     * @param userId 사용자 ID
     * @param request 금액
     * @return
     */
    @PostMapping("/{userId}/charge")
    public ResponseEntity<UserPointResponseDto> chargePoint(
            @PathVariable String userId,
            @RequestBody UserPointRequestDto request) {
        UserPointResponseDto user = userService.chargePoint(userId, request.amount());
        return ResponseEntity.ok(user);
    }

    /**
     * 포인트를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 포인트 반환
     */
    @GetMapping("/{userId}/balance")
    public ResponseEntity<UserPointResponseDto> getBalance(@PathVariable String userId) {
        UserPointResponseDto balance = userService.getBalance(userId);
        return ResponseEntity.ok(balance);
    }
}
