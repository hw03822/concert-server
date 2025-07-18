package kr.hhplus.be.server.concert.service;

import kr.hhplus.be.server.concert.domain.SoldoutRank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ConcertRankingServiceTest {

    @Autowired
    private ConcertRankingService concertRankingService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String WEEKLY_RANKING_KEY = "ranking:weekly";

    @BeforeEach
    void setUp() {
        redisTemplate.delete(WEEKLY_RANKING_KEY);
    }

    @Test
    @DisplayName("점수에 따라 상위 랭킹 순으로 조회된다.")
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

}