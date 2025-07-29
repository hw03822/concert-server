package kr.hhplus.be.server.concert.service;

import kr.hhplus.be.server.concert.domain.SoldoutRank;
import kr.hhplus.be.server.concert.repository.SoldoutRankJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ConcertRankingServiceTest {

    @Autowired
    private ConcertRankingService concertRankingService;

    @Autowired
    private SoldoutRankJpaRepository soldoutRankJpaRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CacheManager cacheManager;

    private static final String WEEKLY_RANKING_KEY = "ranking:weekly";

    @BeforeEach
    void setUp() {
        redisTemplate.delete(WEEKLY_RANKING_KEY);
    }

    @Test
    @DisplayName("점수에 따라 상위 랭킹 순으로 조회된다.(캐시X)")
    void testAddAndGetTopRankings() {
        // given
        redisTemplate.opsForZSet().add(WEEKLY_RANKING_KEY, 1L, 100);
        redisTemplate.opsForZSet().add(WEEKLY_RANKING_KEY, 2L, 1000);
        redisTemplate.opsForZSet().add(WEEKLY_RANKING_KEY, 3L, 900);

        // when
        List<SoldoutRank> topList = concertRankingService.getTopRankings(3);

        // then
        assertThat(topList).hasSizeGreaterThanOrEqualTo(3);

        assertThat(topList.get(0).getConcertId()).isEqualTo(1L);
        assertThat(topList.get(1).getConcertId()).isEqualTo(3L);
        assertThat(topList.get(2).getConcertId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("매진 랭킹 업데이트 시 DB에 모두 저장된다.")
    void updateSoldOutRanking_savesToRedisAndDB() {
        // given
        Long concertId = 1L;
        LocalDateTime openedAt = LocalDateTime.now().minusMinutes(5);
        LocalDateTime soldoutAt = LocalDateTime.now();
        int seatTotal = 150;

        // when
        concertRankingService.updateSoldOutRanking(concertId, openedAt, soldoutAt, seatTotal);

        // then
        List<SoldoutRank> rankList = soldoutRankJpaRepository.findTopRankingWithLimit(10);
        assertThat(rankList.get(0).getConcertId()).isEqualTo(concertId);
        assertThat(rankList.get(0).getSeatTotal()).isEqualTo(seatTotal);
    }

    @Test
    @DisplayName("오류 발생 시에 예외를 던지지 않고 처리한다.")
    void updateSoldOutRanking_whenException() {
        // given
        long concertId = 999L;
        LocalDateTime openedAt = null;
        LocalDateTime soldoutAt = LocalDateTime.now();
        int seatTotal = 50;

        // when & then
        assertDoesNotThrow(() -> {
            concertRankingService.updateSoldOutRanking(concertId, openedAt, soldoutAt, seatTotal);
        });
    }

    @Test
    @DisplayName("랭킹 조회시 Cacheable 메서드는 한번만 실행되어야한다.(캐시O)")
    void cacheableMethod_shouldBeCalledOnlyOnce() {
        // given
        // 캐시 초기 상태 확인
        assertNull(cacheManager.getCache("SoldOutRanking").get(10));

        // when
        // 첫 번째 호출 -> 캐시 미스 -> 실제 메서드 실행 (DB 접근)
        List<SoldoutRank> result1 = concertRankingService.getSoldOutRankingCache(10);

        // 캐시 저장됐는지 확인
        Cache.ValueWrapper cached = cacheManager.getCache("SoldOutRanking").get(10);
        assertNotNull(cached);

        // 두 번째 호출 -> 캐시 있음 -> 캐시에서 가져옴
        List<SoldoutRank> result2 = concertRankingService.getSoldOutRankingCache(10);

        // then
        // 두 호출 결과 동일
        assertEquals(result1, result2);
    }

}