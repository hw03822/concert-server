package kr.hhplus.be.server.concert.service;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.dto.ConcertResponseDto;
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @InjectMocks
    private ConcertService concertService;

    @Mock
    private ConcertJpaRepository concertJpaRepository;

    private Concert concert1;
    private Concert concert2;
    private Concert pastConcert;
    private List<Concert> concertList;

    @BeforeEach
    void setUp() {
        concert1 = new Concert(
                1L,
                "concert1",
                "artist1",
                LocalDateTime.of(2025, 7, 13, 14, 00, 00)
        );

        concert2 = new Concert(
                2L,
                "concert2",
                "artist2",
                LocalDateTime.of(2025, 7, 25, 19, 00, 00)
        );

        pastConcert = new Concert(
                3L,
                "pastConcert",
                "artist3",
                LocalDateTime.of(2023, 7, 25, 19, 00, 00)
        );

        concertList = Arrays.asList(concert1, concert2, pastConcert);
    }

    @Test
    @DisplayName("현재 날짜를 기준으로 예약 가능한 콘서트 목록을 조회한다.")
    void getAvailableConcerts_ReturnConcertList() {
        //given
        when(concertJpaRepository.findAll()).thenReturn(concertList);

        //when
        List<ConcertResponseDto> availableConcerts = concertService.getAvailableConcerts();

        //then
        assertThat(availableConcerts).isNotEmpty();
        assertThat(availableConcerts).hasSize(2);
        assertThat(availableConcerts).extracting("title")
                .containsExactly("concert1", "concert2");
        assertThat(availableConcerts).extracting("artist")
                .containsExactly("artist1", "artist2");
    }
}