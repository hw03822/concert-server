package kr.hhplus.be.server.integration;

import jakarta.transaction.Transactional;
import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.queue.domain.QueueToken;
import kr.hhplus.be.server.queue.dto.QueueTokenRequestDto;
import kr.hhplus.be.server.reservation.dto.ReservationRequestDto;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataHelper testDataHelper; // 테스트 데이터용

    private User user;
    private String userId;
    private QueueTokenRequestDto tokenRequest;


    private ReservationRequestDto reservationRequest;
    private Seat seat;
    private Long seatId;
    private Integer seatNumber;

    private Concert concert;
    private Long concertId;

    @BeforeEach
    void setUp() {
        user = testDataHelper.createUser();
        userId = user.getUserId();
        tokenRequest = new QueueTokenRequestDto(userId);

        seat = testDataHelper.createSeat();
        seatId = seat.getSeatId();
        seatNumber = seat.getSeatNumber();

        concert = testDataHelper.createConcert();
        concertId = concert.getConcertId();
        reservationRequest = new ReservationRequestDto(userId, concertId, seatNumber);
    }

    @Test
    @DisplayName("전체 플로우 통합 테스트 : 토큰 발급 -> 좌석 예약 -> 결제 완료")
    void fullReservationFlow_TokenToPayment_ShouldWorkCorrectly() throws Exception {
        // 1. 토큰 발급 요청
        String tokenResponse = mockMvc.perform(post("/api/v1/queue/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tokenRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.queuePosition").value(0))
                .andExpect(jsonPath("$.estimatedWaitTimeMinutes").value(0))
                .andExpect(jsonPath("$.status").value(QueueToken.QueueStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.issuedAt").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(tokenResponse).get("token").asText();

        logger.info("토큰 발급 성공 : {}", token);

        // 2. 좌석 예약 요청
        mockMvc.perform(post("/api/v1/reservations")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reservationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").exists())
                .andExpect(jsonPath("$.seatId").value(seatId))
                .andExpect(jsonPath("$.concertId").value(concertId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.seatNum").value(20))
                .andExpect(jsonPath("$.price").value(50000))
                .andExpect(jsonPath("$.confirmedAt").exists())
                .andExpect(jsonPath("$.expiredAt").exists());

        // 3. 결제 요청
    }
}
