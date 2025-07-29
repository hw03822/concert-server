package kr.hhplus.be.server.concert.listener;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.domain.SoldoutRank;
import kr.hhplus.be.server.concert.event.ConcertSoldOutEvent;
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository;
import kr.hhplus.be.server.concert.repository.SoldoutRankJpaRepository;
import kr.hhplus.be.server.concert.service.ConcertRankingService;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class RankingEventListenerTest {

    @MockitoBean
    private ConcertJpaRepository concertJpaRepository;

    @MockitoBean
    private SeatJpaRepository seatJpaRepository;

    @MockitoBean
    private ConcertRankingService concertRankingService;

    @Autowired
    private RankingEventListener rankingEventListener;

    @Autowired
    private SoldoutRankJpaRepository soldoutRankJpaRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCache("SoldOutRanking").clear();

    }

    @Test
    @Transactional
    @DisplayName("좌석이 모두 예약되었을 때 캐시를 삭제하고 매진 랭킹을 업데이트한다.")
    void whenAllSeatsReserved_thenClearCacheAndUpdateRanking() {
        // given
        Long concertId = 1L;
        Concert concert = new Concert(
                concertId,
                "concert1",
                "artist1",
                150,
                LocalDateTime.of(2025, 7, 13, 14, 0, 0),
                LocalDateTime.of(2025, 7, 13, 14, 0, 0)
        );

        // 캐시에 테스트용 데이터 넣기 (조회하면서)
        List<SoldoutRank> result1 = concertRankingService.getSoldOutRankingCache(10);

        when(seatJpaRepository.countByConcertIdAndStatusNot(concertId, Seat.SeatStatus.RESERVED)).thenReturn(0L);
        when(concertJpaRepository.findByConcertId(concertId)).thenReturn(Optional.of(concert));

        ConcertSoldOutEvent event = new ConcertSoldOutEvent(concertId, LocalDateTime.now());

        // when
        rankingEventListener.handleConcertSoldOut(event);

        // then
        // 1. 캐시가 삭제되었는지 검증
        Cache.ValueWrapper cached = cacheManager.getCache("SoldOutRanking").get(10);
        assertNull(cached);

        // 2. 랭킹 업데이트 메서드가 호출되었는지 검증
        verify(concertRankingService, times(1)).clearSoldOutRankingCache();
        verify(concertRankingService, times(1)).updateSoldOutRanking(
                eq(concertId),
                eq(concert.getOpenTime()),
                any(LocalDateTime.class),
                eq(concert.getSeatTotal())
        );
    }
}