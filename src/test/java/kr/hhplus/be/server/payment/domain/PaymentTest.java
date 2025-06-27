package kr.hhplus.be.server.payment.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

class PaymentTest {
    @Test
    @DisplayName("정상적인 결제 생성 시 COMPLETED 상태로 생성된다")
    void createPayment_ShouldBeCompleted() {
        // given
        String reservationId = "reservation-123";
        String userId = "user-456";
        Long price = 50000L;

        // when
        Payment payment = new Payment(reservationId, userId, price);

        // then
        assertNotNull(payment.getPaymentId());
        assertEquals(reservationId, payment.getReservationId());
        assertEquals(userId, payment.getUserId());
        assertEquals(price, payment.getPrice());
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.getStatus());
        assertTrue(payment.isCompleted());
        assertFalse(payment.isFailed());
        assertTrue(payment.isRefundable());
    }

        @Test
        @DisplayName("결제 실패 처리하면 FAILED 상태가 된다")
        void failPayment_ShouldChangeToFailed() {
            // given
            Payment payment = new Payment("reservation-123", "user-456", 50000L);

            // when
            payment.fail();

            // then
            assertEquals(Payment.PaymentStatus.FAILED, payment.getStatus());
            assertFalse(payment.isCompleted());
            assertTrue(payment.isFailed());
            assertFalse(payment.isRefundable());
        }

        @Test
        @DisplayName("완료된 결제를 취소하면 CANCELLED 상태가 된다")
        void cancelPayment_ShouldChangeToCancelled() {
            // given
            Payment payment = new Payment("reservation-123", "user-456", 50000L);
            assertTrue(payment.isCompleted());

            // when
            payment.cancel();

            // then
            assertEquals(Payment.PaymentStatus.CANCELLED, payment.getStatus());
            assertFalse(payment.isCompleted());
            assertFalse(payment.isFailed());
            assertFalse(payment.isRefundable());
        }

}