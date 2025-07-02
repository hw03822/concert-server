package kr.hhplus.be.server.payment.controller;

import kr.hhplus.be.server.payment.application.PaymentService;
import kr.hhplus.be.server.payment.application.input.PaymentCommand;
import kr.hhplus.be.server.payment.application.output.PaymentResult;
import kr.hhplus.be.server.payment.dto.PaymentRequestDto;
import kr.hhplus.be.server.payment.dto.PaymentResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 결제 관리 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 진행
     * POST /api/v1/payments
     */
    @PostMapping
    public ResponseEntity<PaymentResponseDto> processPayment(@RequestBody PaymentRequestDto request) {
        PaymentCommand command = new PaymentCommand(request.getReservationId(), request.getUserId());
        PaymentResult result = paymentService.processPayment(command);
        PaymentResponseDto response = PaymentResponseDto.from(result);

        return ResponseEntity.ok(response);
    }

    /**
     * 좌석 예약 상태 조회
     * GET /api/v1/payments/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponseDto> getPaymentInfo(@PathVariable String paymentId) {
        PaymentResult result = paymentService.getPaymentInfo(paymentId);
        PaymentResponseDto response = PaymentResponseDto.from(result);

        return ResponseEntity.ok(response);
    }


}
