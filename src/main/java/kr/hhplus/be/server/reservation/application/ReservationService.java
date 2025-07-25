package kr.hhplus.be.server.reservation.application;

import kr.hhplus.be.server.common.RedisKeyUtils;
import kr.hhplus.be.server.common.lock.RedisDistributedLock;
import kr.hhplus.be.server.queue.service.QueueService;
import kr.hhplus.be.server.reservation.application.input.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.application.output.ReserveSeatResult;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationRepository;
import kr.hhplus.be.server.reservation.event.ReservationCompletedEvent;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService{

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final SeatJpaRepository seatJpaRepository;
    private final ReservationRepository reservationRepository;
    private final RedisDistributedLock redisDistributedLock;
    private final QueueService queueService;
    private final ApplicationEventPublisher eventPublisher;

    // 예약 만료 시간 (5분)
    @Value("${reservation.ttl.minutes:5}")
    private int reservationTTLMinutes;

    /**
     * 좌석 예약 기능
     * @param command 예약 요청 정보
     * @return 생성된 예약 정보
     */
    public ReserveSeatResult reserveSeat(ReserveSeatCommand command, String token) {
        // 1. 토큰 유효한지 확인 (token은 controller에서 넘겨준다 가정)
        boolean isValid = queueService.validateActiveToken(token);
        if(!isValid) {
            throw new IllegalStateException("유효하지 않은 토큰입니다.");
        }

        log.info("[reserveSeat] 유효한 토큰입니다.");

        // 2. 분산락 획득 (동시성 문제)
        String seatLockKey = RedisKeyUtils.seatLockKey(command.getConcertId(), command.getSeatNumber());
        String seatLockValue = command.getUserId();

        // 분산 락 획득 시도
        if(!redisDistributedLock.tryLockWithRetry(seatLockKey, seatLockValue, reservationTTLMinutes)) {
            log.info("[reserveSeat] 분산 락 획득 실패");
            throw new RuntimeException("대기열 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        log.info("[reserveSeat] 락 획득 seatLockKey : {}, seatLockValue : {}", seatLockKey, seatLockValue);

        try {
            // 좌석 예약 로직
            return reserveSeatWithTransaction(command);
        } finally {
            // 3. 분산락 해제
            redisDistributedLock.releaseLock(seatLockKey, seatLockValue);
        }
    }

    /**
     * 좌석 예약 기능 (트랜잭션)
     * @param command 예약 요청 정보
     * @return 생성된 예약 정보
     */
    @Transactional
    protected ReserveSeatResult reserveSeatWithTransaction(ReserveSeatCommand command) {
        // 1. 좌석 조회 및 상태 확인
        Seat seat = seatJpaRepository.findByConcertIdAndSeatNumber(command.getConcertId(), command.getSeatNumber());
        if(seat == null) {
            throw new IllegalStateException("좌석이 존재하지 않습니다.");
        }

        // 2. 이용 가능한 좌석인지 확인
        if(!seat.isAvailable()){
            // 임시 배정 만료 시
            if(seat.isExpired()){
                // 임시 배정 해제
                seat.releaseAssign();
                // 변경된 상태 DB에 반영
                seatJpaRepository.save(seat);
            } else {
                throw new IllegalStateException("이미 선택된 좌석입니다.");
            }
        }

        // 2-1. 이용 가능하면 임시 배정 처리
        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(5);
        seat.assign(expiredAt);
        seatJpaRepository.save(seat);

        // 2-2. 예약 생성
        Reservation reservation = new Reservation(
                command.getUserId(),
                command.getConcertId(),
                seat.getSeatId(),
                expiredAt,
                seat.getPrice(),
                command.getSeatNumber()
        );
        reservationRepository.save(reservation);

        // 3. 예약 정보 이벤트 발행 (최소한의 데이터만 전달)
        ReservationCompletedEvent event = new ReservationCompletedEvent(reservation.getReservationId());
        eventPublisher.publishEvent(event);

        return new ReserveSeatResult(reservation);
    }

    /**
     * 예약 상태 조회
     * @param reservationId 예약 ID
     * @return 예약 상태 정보
     */
    @Transactional
    public ReserveSeatResult getReservationStatus(String reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 예약입니다."));

        Seat seat = seatJpaRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new IllegalStateException("좌석 정보를 찾을 수 없습니다."));

        return new ReserveSeatResult(reservation);
    }

    /**
     * 예약 취소 기능
     * @param userId 사용자 ID
     * @param reservationId 예약 ID
     */
    @Transactional
    public void cancelReservation(String userId, String reservationId) {
       // 1. 예약 정보 찾기
        Reservation reservation = reservationRepository.findById(reservationId)
               .orElseThrow(() -> new IllegalStateException("존재하지 않는 예약입니다."));

       if (!reservation.getUserId().equals(userId)) {
           throw new IllegalStateException("예약 취소할 권한이 없습니다.");
       }

       // 2. 예약 상태 변경 TEMPORARILY_ASSIGNED -> CANCELLED (취소)
       reservation.cancel();
       reservationRepository.save(reservation);

       // 3. 좌석 상태 변경 TEMPORARILY_ASSIGNED -> AVAILABLE (좌석 해제)
        Seat seat = seatJpaRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new IllegalStateException("좌석 정보를 찾을 수 없습니다."));

        seat.releaseAssign();
        seatJpaRepository.save(seat);
    }

    /**
     * 만료된 예약 해제
     */
    public void releaseExpiredReservations() {
        // 1. 만료된 예약 조회 (status:TEMPORARILY_ASSIGNED, expiredAt 지남)
        List<Reservation> expiredReservations = reservationRepository.findByStatusAndExpiredAtBefore(
                Reservation.ReservationStatus.TEMPORARILY_ASSIGNED,
                LocalDateTime.now()
        );

        for (Reservation reservation : expiredReservations) {
            try {
                // 2. 하나의 예약에 대한 만료 처리 & 좌석 해제
                releaseExpiredOneReservation(reservation);
            } catch (Exception e) {
                // 개별 예약 실패는 로깅만 하고 다음 작업 반복
                log.info("[ReservationService.releaseExpiredReservations] 예약 해제 실패, reservationId={}, seatId={}, error={}"
                        ,reservation.getReservationId(), reservation.getSeatId(), e.getMessage());
            }
        }
    }

    /**
     * 하나의 예약에 대해 만료 처리 & 좌석 해제
     * 트랜잭션 경계 조정
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseExpiredOneReservation(Reservation reservation) {
        // 1. 예약 상태 변경 (만료 처리)
        reservation.expire();

        // 2. 좌석 해제 (TEMPORARILY_ASSIGNED -> AVAILABLE)
        Seat seat = seatJpaRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new IllegalStateException("좌석 정보를 찾을 수 없습니다. seatId : {}" + reservation.getSeatId()));

        seat.releaseAssign();

        // 3. 좌석 정보 저장
        seatJpaRepository.save(seat);

        // 4. 예약 정보 저장
        reservationRepository.save(reservation);
    }
}
