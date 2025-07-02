package kr.hhplus.be.server.payment.dto;

import lombok.Getter;

@Getter
public class PaymentRequestDto {
    private String reservationId;
    private String userId;
}
