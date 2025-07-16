package kr.hhplus.be.server.concert.service;

import kr.hhplus.be.server.common.RedisKeyUtils;
import kr.hhplus.be.server.concert.domain.SoldoutRank;
import kr.hhplus.be.server.concert.repository.SoldoutRankJpaRepository;
import kr.hhplus.be.server.queue.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ConcertRankingService {
    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final SoldoutRankJpaRepository soldoutRankJpaRepository;



    public ConcertRankingService(RedisTemplate<String, Object> redisTemplate, SoldoutRankJpaRepository soldoutRankJpaRepository) {
        this.redisTemplate = redisTemplate;
        this.soldoutRankJpaRepository = soldoutRankJpaRepository;
    }

    /**
     * 매진 랭킹 조회 (상위 N개)
     * @param limit 상위 N개
     * @return 랭킹 리스트
     */
    public List<SoldoutRank> getTopRankings(int limit) {
        try {
            // 1. Redis 에서 상위 랭킹 조회

            Set<ZSetOperations.TypedTuple<Object>> rankings =
                    redisTemplate.opsForZSet().rangeWithScores(RedisKeyUtils.weeklyRankingKey(), 0, limit - 1);

            List<SoldoutRank> result = new ArrayList<>();

            // 2. Redis에서 반환되는 값 변환
            for (ZSetOperations.TypedTuple<Object> tuple : rankings) {
                Long concertId = Long.valueOf(tuple.getValue().toString());
                Double score = tuple.getScore();

                SoldoutRank ranking = SoldoutRank.builder()
                        .concertId(concertId)
                        .score(score)
                        .build();

                result.add(ranking);
            }
            return result;
        } catch (Exception e) {
            log.info("매진 랭킹 조회 실패 limit: {}", limit, e);
            return new ArrayList<>();
        }
    }
}
