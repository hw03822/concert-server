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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
                long score = tuple.getScore().longValue();

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

    /**
     * 콘서트 매진 시 매진 랭킹 업데이트 (이벤트 리스너에서 사용)
     * @param concertId 콘서트 ID
     * @param ticketOpenedAt 티켓 오픈 시간
     * @param soldoutAt 매진 시간
     * @param seatTotal 좌석 수
     */
    public void updateSoldOutRanking(Long concertId, LocalDateTime ticketOpenedAt, LocalDateTime soldoutAt, int seatTotal) {
        try {
            // 1. 매진 랭킹 점수 산정
            long soldOutSeconds = Duration.between(ticketOpenedAt, soldoutAt).getSeconds();
            long openTimeStamp = ticketOpenedAt.toEpochSecond(ZoneOffset.UTC);

            // 점수가 작을 수록 매진이 빠름
            long score = soldOutSeconds + (100000 - seatTotal) + openTimeStamp;

            // 2. Redis에 매진 랭킹 업데이트
            redisTemplate.opsForZSet().add(RedisKeyUtils.weeklyRankingKey(), concertId, score);

            // 3. DB에 저장 (백업용)
            SoldoutRank soldoutRank = SoldoutRank.builder()
                    .concertId(concertId)
                    .ticketOpenedAt(ticketOpenedAt)
                    .soldoutAt(soldoutAt)
                    .seatTotal(seatTotal)
                    .score(score)
                    .build();

            soldoutRankJpaRepository.save(soldoutRank);

            log.info("매진 랭킹 업데이트 완료 concertId : {}, 매진 시간 : {}초, 점수 : {}",
                    concertId, soldOutSeconds, score);

        } catch (Exception e) {
            log.info("매진 랭킹 업데이트 실패 concertId : {}", concertId, e);
        }
    }
}
