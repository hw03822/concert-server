## 좌석 예약 Reservation flow 보고서
### ✅ 개요
- 콘서트 예약 서비스의 핵심 모듈인 좌석 예약 Reservation 구조, 흐름, 비즈니스 정책을 정리

### 1️⃣ 구조
```
reservation/
├── application/
│   ├── ReservationService.java          # 핵심 비즈니스 로직
│   ├── input/ReserveSeatCommand.java    # 입력 커맨드
│   └── output/ReserveSeatResult.java    # 출력 결과
├── controller/
│   └── ReservationController.java       # REST API 엔드포인트
├── domain/
│   ├── Reservation.java                 # 예약 도메인 엔티티
│   └── ReservationRepository.java       # 도메인 레포지토리 인터페이스
├── dto/
│   ├── ReservationRequestDto.java      # 요청 DTO
│   └── ReservationResponseDto.java      # 응답 DTO
├── event/
│   └── ReservationCompletedEvent.java   # 예약 완료 이벤트
├── listener/
│   └── ReservationEventListener.java   # 이벤트 리스너
└── infrastructure/
    └── persistence/                     # 영속성 계층
```

### 2️⃣ 프로세스 흐름
```Mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant QueueService
    participant ReservationService
    participant RedisLock
    participant Database
    participant EventPublisher
    participant KafkaProducer

    Client->>Controller: POST /api/v1/reservations
    Controller->>QueueService: 토큰 유효성 검증
    QueueService-->>Controller: 토큰 유효성 결과
    
    Controller->>ReservationService: reserveSeat(command, token)
    ReservationService->>RedisLock: 분산락 획득 시도
    RedisLock-->>ReservationService: 락 획득 성공/실패
    
    ReservationService->>Database: 좌석 상태 조회
    Database-->>ReservationService: 좌석 정보 반환
    
    alt 좌석 이용 가능
        ReservationService->>Database: 좌석 임시 배정 (5분 TTL)
        ReservationService->>Database: 예약 생성 (TEMPORARILY_ASSIGNED)
        ReservationService->>EventPublisher: ReservationCompletedEvent 발행
        ReservationService->>KafkaProducer: Kafka 메시지 발행
        ReservationService->>RedisLock: 분산락 해제
        ReservationService-->>Controller: 예약 결과 반환
    else 좌석 이용 불가
        ReservationService->>RedisLock: 분산락 해제
        ReservationService-->>Controller: 예외 발생
    end
```

### 3️⃣ 상태 관리
**예약 상태 (ReservationStatus)**
  - `TEMPORARILY_ASSIGNED`: 임시 배정 (5분 유효)
  - `CONFIRMED`: 결제 완료로 확정
  - `CANCELLED`: 사용자 취소
  - `EXPIRED`: 시간 만료
**상태 전이 규칙**
  1. `TEMPORARILY_ASSIGNED` → `CONFIRMED`: 결제 완료 시
  2. `TEMPORARILY_ASSIGNED` → `CANCELLED`: 사용자 취소 시
  3. `TEMPORARILY_ASSIGNED` → `EXPIRED`: 5분 경과 시

### 4️⃣ 핵심 비즈니스 정책
**1. 예약 만료 정책** 
- 예약 유효 시간 : 5분 (설정 : reservation.ttl.minutes)
- 자동 만료 처리 : 만료된 예약 정리
- 좌석 해제 : 만료 시 좌석 상태를 `AVAILABLE`로 복원
**2. 동시성 제어 정책**
- 분산락 사용 : Redis 기반 분산락으로 동시 예약 방지
- Lock Key : `lock:seat:{concertId}:{seatNumber}`
- Lock TTL : 예약 만료 시간과 동일 (5분)
- 재시도 매커니즘 : 락 획득 실패 시 재시도 로직
**3. 대기열 시스템 연동**
- 토큰 검증 : 예약 전 대기열 토큰 유효성 확인
- 활성 사용자 제한 : 최대 100명 동시 예약 가능
- 토큰 만료 : 30분 후 자동 만료

### 5️⃣ 비즈니스 규칙
1. 대기열 토큰 필수
2. 좌석별 분산락으로 동시성 제어
3. 5분 내 결제 필수
4. 만료 시 자동 좌석 해제

### 6️⃣ 예약 만료 & 좌석 해체 처리 문제점 및 개선 방안
1. 예약 생성 시 : 좌석이 `TEMPORARILY_ASSIGNED` 상태로 변경
2. 예약이 만료 시간이 지나면 자동으로 예약 만료와 좌석 해제 처리되어야 함
3. 다른 사용자가 같은 좌석에 접근할 때 좌석 해제됨 
**⚠️ 문제점**
| 구분              | 문제 내용                                           |
| --------------- | ----------------------------------------------- |
| **자동 만료 미작동**   | 예약이 만료되어도 스케줄러가 없어 자동 상태 전환이 이루어지지 않음           |
| **좌석 점유 지속**    | 만료된 예약이 좌석을 계속 점유하여 신규 예약 불가                    |
| **수동 개입 필요**    | 수동으로 `releaseExpiredReservations()` 호출해야 함 |
| **DB 부하 고려 미흡** | 전체 예약 스캔 방식으로 주기적 실행 시 부하 가능                    |
| **만료처리 되지않은 좌석 접근** | 만료된 좌석은 접근 시점에서 해제 처리되는데 만료 처리가 되지 않아 만료된 좌석이 장시간 점유됨      |

**✅ 개선 방향**
1. 예약 만료 자동 처리 스케줄러 추가
- 주기적으로 함수를 실행하여 자동 만료 처리
- DB 조회하기 때문에 스케줄러 주기 설정 시 주의
2. 좌석 해제 시점
- 스케줄러 + 사용자 접근 시 해제 병행
