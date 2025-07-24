package kr.hhplus.be.server.concert.listener;

import kr.hhplus.be.server.concert.domain.Concert;
import kr.hhplus.be.server.concert.event.ConcertSoldOutEvent;
import kr.hhplus.be.server.concert.repository.ConcertJpaRepository;
import kr.hhplus.be.server.concert.service.ConcertRankingService;
import kr.hhplus.be.server.queue.service.QueueService;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RankingEventListener {
    private static final Logger log = LoggerFactory.getLogger(RankingEventListener.class);

    private final SeatJpaRepository seatJpaRepository;
    private final ConcertJpaRepository concertJpaRepository;
    private final ConcertRankingService rankingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleConcertSoldOut(ConcertSoldOutEvent event) {
        try {
            // 1. 좌석 매진 상태 감지

            long notReservedCount = seatJpaRepository.countByConcertIdAndStatusNot(
                    event.getConcertId(),
                    Seat.SeatStatus.RESERVED
            );

            // 아직 매진이 아닐 경우
            if (notReservedCount > 0) {
                return;
            }

            // 2. 콘서트 정보 가져오기
            Concert concert = concertJpaRepository.findByConcertId(event.getConcertId())
                    .orElseThrow(() -> new IllegalStateException("콘서트 정보를 찾지 못했습니다."));

            // 3. 매진 시, 캐시 무효화
            rankingService.clearSoldOutRankingCache();

            // 4. 매진 랭킹 업데이트
            rankingService.updateSoldOutRanking(
                event.getConcertId(), concert.getOpenTime(), event.getSoldoutAt(), concert.getSeatTotal()
            );

        } catch (Exception e) {
            log.info("매진 랭킹 이벤트 처리 중 오류 발생 - concertId : {}", event.getConcertId(), e);
        }
    }
}
