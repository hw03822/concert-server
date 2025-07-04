package kr.hhplus.be.server.reservation.application;

import jakarta.transaction.Transactional;
import kr.hhplus.be.server.common.RedisKeyUtils;
import kr.hhplus.be.server.common.lock.RedisDistributedLock;
import kr.hhplus.be.server.queue.service.QueueService;
import kr.hhplus.be.server.reservation.application.input.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.application.output.ReserveSeatResult;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationRepository;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService{

    private final SeatJpaRepository seatJpaRepository;
    private final ReservationRepository reservationRepository;
    private final RedisDistributedLock redisDistributedLock;
    private final QueueService queueService;

    // 예약 만료 시간 (5분)
    @Value("${reservation.ttl.minutes:5}")
    private int reservationTTLMinutes;

    public ReservationService(SeatJpaRepository seatJpaRepository, ReservationRepository reservationRepository, RedisDistributedLock redisDistributedLock, QueueService queueService, RedisTemplate<String, Object> redisTemplate) {
        this.seatJpaRepository = seatJpaRepository;
        this.reservationRepository = reservationRepository;
        this.redisDistributedLock = redisDistributedLock;
        this.queueService = queueService;
    }

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

        // 2. 분산락 획득 (동시성 문제)
        String seatLockKey = RedisKeyUtils.seatLockKey(command.getConcertId(), command.getSeatNumber());
        String seatLockValue = command.getUserId();

        // 분산 락 획득 시도
        if(!redisDistributedLock.tryLockWithRetry(seatLockKey, seatLockValue, reservationTTLMinutes)) {
            throw new RuntimeException("대기열 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

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

    public void releaseExpiredReservations() {
        // 1. 만료된 예약 조회 (status:TEMPORARILY_ASSIGNED, expiredAt 지남)
        List<Reservation> expiredReservations = reservationRepository.findByStatusAndExpiredAtBefore(
                Reservation.ReservationStatus.TEMPORARILY_ASSIGNED,
                LocalDateTime.now()
        );

        // 2. 예약 상태 변경 (만료 처리)
        for (Reservation reservation : expiredReservations) {
            reservation.expire();

            // 3. 좌석 해제 (TEMPORARILY_ASSIGNED -> AVAILABLE)
            Seat seat = seatJpaRepository.findById(reservation.getSeatId())
                    .orElseThrow(() -> new IllegalStateException("좌석 정보를 찾을 수 없습니다."));

            seat.releaseAssign();
            seatJpaRepository.save(seat);
        }

        // 4. 일괄 저장
        reservationRepository.saveAll(expiredReservations);
    }
}
