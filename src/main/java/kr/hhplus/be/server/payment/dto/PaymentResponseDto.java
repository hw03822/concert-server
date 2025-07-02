package kr.hhplus.be.server.payment.dto;

import kr.hhplus.be.server.payment.application.output.PaymentResult;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PaymentResponseDto {
    private String paymentId;
    private String reservationId;
    private String userId;
    private Long price;
    private String status;
    private LocalDateTime paidAt;

    public static PaymentResponseDto from(PaymentResult result) {
        PaymentResponseDto dto = new PaymentResponseDto();
        dto.paymentId = result.getPaymentId();
        dto.reservationId = result.getReservationId();
        dto.userId = result.getUserId();
        dto.price = result.getPrice();
        dto.status = result.getStatus();
        dto.paidAt = result.getPaidAt();
        return dto;
    }
}
