package kr.hhplus.be.server.reservation.application;

import kr.hhplus.be.server.common.lock.RedisDistributedLock;
import kr.hhplus.be.server.queue.service.QueueService;
import kr.hhplus.be.server.reservation.domain.Reservation;
import kr.hhplus.be.server.reservation.domain.ReservationRepository;
import kr.hhplus.be.server.reservation.application.input.ReserveSeatCommand;
import kr.hhplus.be.server.reservation.application.output.ReserveSeatResult;
import kr.hhplus.be.server.seat.domain.Seat;
import kr.hhplus.be.server.seat.repository.SeatJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private SeatJpaRepository seatJpaRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RedisDistributedLock redisDistributedLock;

    @Mock
    private QueueService queueService;

    @InjectMocks
    private ReservationService reservationService;

    private Seat availableSeat;
    private ReserveSeatCommand command;
    private String token;

    @BeforeEach
    void setUp() {
        command = new ReserveSeatCommand("user-123", 1L, 20);
        availableSeat = new Seat(1L, 1L, 20, 100000);
        token = "active-token-123";
    }

    @Test
    @DisplayName("정상적인 좌석 예약 요청 시 임시 배정이 성공한다")
    void whenReserveSeatWithValidRequest_ThenShouldSucceed() {
        //given
        given(queueService.validateActiveToken(token)).willReturn(true);
        given(redisDistributedLock.tryLockWithRetry(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatJpaRepository.findByConcertIdAndSeatNumber(1L, 20)).willReturn(availableSeat);
        given(seatJpaRepository.save(any(Seat.class))).willReturn(availableSeat);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        //when
        ReserveSeatResult result = reservationService.reserveSeat(command, token);

        //then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getConcertId()).isEqualTo(1L);
        assertThat(result.getSeatNum()).isEqualTo(20);
        assertThat(result.getPrice()).isEqualTo(100000);
        assertThat(result.getRemainingTimeSeconds()).isGreaterThan(0);

        // 검증:활성 토큰 검증이 호출되었는지 확인
        verify(queueService).validateActiveToken(token);

        // 검증:좌석이 임시 배정 되었는지 확인
        verify(seatJpaRepository).save(argThat(seat -> seat.getStatus() == Seat.SeatStatus.TEMPORARILY_ASSIGNED));

        // 검증:예약이 생성되었는지 확인
        verify(reservationRepository).save(argThat(reservation ->
                reservation.getUserId().equals("user-123") &&
                reservation.getStatus() == Reservation.ReservationStatus.TEMPORARILY_ASSIGNED)
        );

        // 검증:락이 해제되었는지 확인
        verify(redisDistributedLock).releaseLock(anyString(), anyString());
    }

    @Test
    @DisplayName("이미 예약된 좌석을 예약하려고 하면 예외가 발생한다.")
    void whenReserveAlreadyReservedSeat_ThenShouldThrowException() {
        //given
        // 좌석 AVAILABLE -> TEMPORARILY_ASSIGNED 로 변경
        availableSeat.assign(LocalDateTime.now().plusMinutes(5));

        given(queueService.validateActiveToken(token)).willReturn(true);
        given(redisDistributedLock.tryLockWithRetry(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatJpaRepository.findByConcertIdAndSeatNumber(1L, 20)).willReturn(availableSeat);

        //when & then
        assertThatThrownBy(() -> reservationService.reserveSeat(command, token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 선택된 좌석입니다.");

        // 검증:락이 해제되었는지 확인
        verify(redisDistributedLock).releaseLock(anyString(), anyString());
    }

    @Test
    @DisplayName("만료된 임시 배정 좌석은 자동으로 해제하고 진행한다.")
    void whenReserveExpiredTemporarilyAssignedSeat_ThenShouldReleaseAndProceed() {
        //given
        availableSeat.assign(LocalDateTime.now().minusMinutes(1));

        given(queueService.validateActiveToken(token)).willReturn(true);
        given(redisDistributedLock.tryLockWithRetry(anyString(), anyString(), anyLong())).willReturn(true);
        given(seatJpaRepository.findByConcertIdAndSeatNumber(1L, 20)).willReturn(availableSeat);
        given(seatJpaRepository.save(any(Seat.class))).willReturn(availableSeat);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        //when
        ReserveSeatResult result = reservationService.reserveSeat(command,token);

        //then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo("user-123");

        // 검증:좌석이 두 번 저장되었는지 확인 (해제+새로운 임시배정)
        verify(seatJpaRepository, times(2)).save(any(Seat.class));
    }

    @Test
    @DisplayName("예약 상태 조회가 정상적으로 동작한다.")
    void whenGetReservationStatus_ThenShouldReturnCorrectInfo() {
        //given
        String reservationId = "reservation-123";
        Reservation reservation = new Reservation(
                "user-123",
                1L,
                1L,
                LocalDateTime.now().plusMinutes(5),
                100000,
                20
        );

        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
        given(seatJpaRepository.findById(1L)).willReturn(Optional.of(availableSeat));

        //when
        ReserveSeatResult result = reservationService.getReservationStatus(reservationId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getReservationId()).isEqualTo(reservation.getReservationId());
        assertThat(result.getUserId()).isEqualTo("user-123");
        assertThat(result.getSeatNum()).isEqualTo(20);
    }

    @Test
    @DisplayName("존재하지 않는 예약 조회 시 예외가 발생한다.")
    void whenGetNonExistentReservation_ThenShouldThrowException() {
        //given
        String reservationId = "non-existing";
        given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

        //when & then
        assertThatThrownBy(() -> reservationService.getReservationStatus(reservationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("존재하지 않는 예약입니다.");
    }
    @Test
    @DisplayName("예약 취소가 정상적으로 동작한다.")
    void whenCancelReservation_ThenShouldSucceed() {
        // given
        String reservationId = "res-123";
        Reservation reservation = new Reservation(
                "user-123",
                1L,
                1L,
                LocalDateTime.now().plusMinutes(5),
                100000,
                20
        );

        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
        availableSeat.assign(LocalDateTime.now().plusMinutes(5)); // 좌석 임시배정된 상태로 변경
        given(seatJpaRepository.findById(1L)).willReturn(Optional.of(availableSeat));

        // when
        reservationService.cancelReservation(reservation.getUserId(), reservationId);

        // then
        // 검증 : 예약 상태 취소 (CANCELLED) 로 변경
        verify(reservationRepository).save(argThat(r ->
                r.getStatus() == Reservation.ReservationStatus.CANCELLED
        ));

        // 검증 : 좌석 상태 이용가능 (AVAILABLE) 로 변경
        verify(seatJpaRepository).save(argThat(s ->
                s.getStatus() == Seat.SeatStatus.AVAILABLE
        ));
    }

    @Test
    @DisplayName("권한 없는 사용자가 예약 취소를 시도하면 예외가 발생한다.")
    void whenUnauthorizedUserTriesToCancel_ThenShouldThrowException() {
        // given
        String reservationId = "res-123";
        String reservationOwner = "user-123";
        String unauthorizedUser = "user-456";

        Reservation reservation = new Reservation(
                reservationOwner,
                1L,
                1L,
                LocalDateTime.now().plusMinutes(5),
                100000,
                20
        );

        given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

        // when & then
        assertThatThrownBy(() -> reservationService.cancelReservation(unauthorizedUser, reservationId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("예약 취소할 권한이 없습니다.");
    }

    @Test
    @DisplayName("만료된 예약들을 일괄 해제한다.")
    void whenReleaseExpiredReservations_ThenShouldProcessAllExpiredReservations() {
        // given
        Reservation expiredReservation1 = mock(Reservation.class);
        Reservation expiredReservation2 = mock(Reservation.class);

        // 예약 내역의 좌석 번호 가져오기
        when(expiredReservation1.getSeatId()).thenReturn(1L);
        when(expiredReservation2.getSeatId()).thenReturn(2L);

        Seat seat1 = new Seat(1L, 1L, 20, 50000);
        Seat seat2 = new Seat(2L, 1L, 21, 50000);

        // 좌석 만료 상태
        seat1.assign(LocalDateTime.now().minusMinutes(1));
        seat2.assign(LocalDateTime.now().minusMinutes(2));

        // 만료된 예약 내역 불러오기
        given(reservationRepository.findByStatusAndExpiredAtBefore(
                eq(Reservation.ReservationStatus.TEMPORARILY_ASSIGNED),
                any(LocalDateTime.class)))
                .willReturn(Arrays.asList(expiredReservation1, expiredReservation2));
        // 좌석 정보 가져오기
        given(seatJpaRepository.findById(1L)).willReturn(Optional.of(seat1));
        given(seatJpaRepository.findById(2L)).willReturn(Optional.of(seat2));

        // when
        reservationService.releaseExpiredReservations();

        // then
        verify(reservationRepository).findByStatusAndExpiredAtBefore(
                eq(Reservation.ReservationStatus.TEMPORARILY_ASSIGNED),
                any(LocalDateTime.class));
        verify(seatJpaRepository, times(2)).findById(any());
        verify(seatJpaRepository, times(2)).save(any(Seat.class));
        verify(reservationRepository).saveAll(any());
    }



}