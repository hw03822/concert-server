package kr.hhplus.be.server.concert.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.hhplus.be.server.concert.dto.ConcertResponseDto;
import kr.hhplus.be.server.concert.service.ConcertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConcertController.class)
class ConcertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConcertService concertService;

    @Test
    @DisplayName("예약 가능한 콘서트 목록을 조회한다.")
    void getAvailableConcerts() throws Exception {
        // given
        ConcertResponseDto concert1 = ConcertResponseDto.builder()
                .concertId(1L)
                .title("concert1")
                .artist("artist1")
                .concertAt(LocalDateTime.of(2025, 7, 13, 14, 00, 00))
                .build();

        ConcertResponseDto concert2 = ConcertResponseDto.builder()
                .concertId(2L)
                .title("concert2")
                .artist("artist2")
                .concertAt(LocalDateTime.of(2025, 7, 25, 19, 00, 00))
                .build();

        List<ConcertResponseDto> concerts = Arrays.asList(concert1, concert2);
        when(concertService.getAvailableConcerts()).thenReturn(concerts);

        // when & then
        mockMvc.perform(get("/api/v1/concerts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].concertId").value(1))
                .andExpect(jsonPath("$[0].title").value("concert1"))
                .andExpect(jsonPath("$[0].artist").value("artist1"))
                .andExpect(jsonPath("$[1].concertId").value(2))
                .andExpect(jsonPath("$[1].title").value("concert2"))
                .andExpect(jsonPath("$[1].artist").value("artist2"));
    }
}
