## 시퀀스 다이어그램(Sequence Diagrams)
## 개요
콘서트 예약 서비스의 주요 API 플로우를 시각화한 시퀀스 다이어그램입니다. 각 다이어그램은 사용자와 시스템 간의 상호작용을 시간 순서대로 보여줍니다.

## 1. 콘서트 정보 조회 및 좌석 정보 조회 플로우
예약 가능한 날짜와 좌석 정보를 조회하는 과정 (대기열 토큰 필요X)

```mermaid
sequenceDiagram
    participant 사용자
    participant server
    participant DB
    사용자->>server: POST /api/v1/concerts
    server->>DB: 예약 가능한 콘서트 조회
    DB-->>server: 콘서트 목록 반환
    server-->>사용자: 날짜 목록 반환
    Note over 사용자, server: dates:["2025-06-09", "2025-06-12"]

    사용자->>server: GET /api/v1/concerts/{concertId}/seats
    server->>DB: 좌석 상태 조회
    DB-->>server: 좌석 목록 반환
    server-->사용자: 좌석 정보 반환
```

## 2. 잔액 충전 플로우
사용자가 결제를 위해 잔액을 충전하는 과정

```mermaid
sequenceDiagram
    participant 사용자
    participant user service
    participant DB
    사용자->>user service: POST /api/v1/users/{userId}/charge
    Note over 사용자, user service: [userId:"user-123", amount:100000]

    user service->>DB: 잔액 충전 요청
    user service->>user service: 충전 금액 유효성 검사
    alt 유효한 충전 금액
    user service->>DB: 트랜잭션 시작
    user service->>DB: 사용자 잔액 조회 (FOR UPDATE)
    DB-->>user service: 현재 잔액 조회
    user service->>DB: 잔액 업데이트
    Note over user service, DB: current_balance + amount

    user service->>DB: 충전 거래 내역 저장
    Note over user service, DB: type: CHARGE <br> amount:100000 <br> current_balance:150000
    user service->>DB: 트랜잭션 커밋
    DB -->> user service : 충전 완료
    user service -->> 사용자: 충전 성공
    end
```

## 3. 대기열 토큰 발급 플로우
사용자가 서비스 이용을 위해 대기열 토큰을 발급받는 과정

```mermaid
sequenceDiagram
    participant 사용자
    participant 대기열 서비스  
    participant Redis

    사용자->>대기열 서비스: POST /api/v1/queue/token
    Note over 사용자, 대기열 서비스: [userId:"user-123", concertId: 1]

    대기열 서비스->>Redis: 기존 토큰 있는지 확인
    대기열 서비스->>Redis: 분산 락 획득 시도

    대기열 서비스->>Redis: 현재 대기열 크기 조회
    Redis-->> 대기열 서비스: 대기열 정보 반환
    대기열 서비스->>대기열 서비스: UUID 생성 및 대기 순서 계산

    대기열 서비스->>Redis: 대기열 유저 추가
    Redis-->>대기열 서비스: 저장 완료
    대기열 서비스-->>사용자: 토큰+대기 정보 반환
```

## 4. 좌석 예약 요청 플로우
사용자가 좌석을 선택하고 임시 배정을 받는 과정 (대기열 토큰 필요O)

```mermaid
sequenceDiagram
    participant 사용자
    participant Reservation service
    participant Redis
    participant DB
    사용자->>Reservation service: GET /api/v1/reservations
    Note over 사용자, Reservation service: [token:"token", concertId:1, seatNumber:20]
    
    Reservation service->>Redis: 토큰 검증(활성 상태 확인)
    Redis-->>Reservation service: 검증 완료(토큰 정보)
    Reservation service->>Redis: 좌석 분산 락 획득 시도
    Note over Reservation service, Redis: Key:"lock:seat:{concertId}:{seatNumber}"

    alt 락 획득 성공
    Redis-->>Reservation service: 락 획득 완료
    Reservation service->>DB: 좌석 상태 조회 (FOR UPDATE)
    DB-->>Reservation service: 좌석 정보 반환
    Reservation service-->>Reservation service: 예약 가능한 좌석인지 확인
    alt 좌석 예약 가능
    Reservation service->>DB: 좌석 임시 배정 처리
    Note over Reservation service, DB: status: AVAILBALE->TEMPORARILY_ASSIGNED <br> assignedUntil: now + 5분
    DB-->>Reservation service: 상태 업데이트 완료
    Reservation service->>Redis: 락 해제 요청
    Redis-->>Reservation service: 해제 완료
    Reservation service->>사용자: 좌석 예약 성공
    end
    end
```

## 5. 결제 처리 플로우
임시 배정된 좌석에 대해 결제를 완료하는 과정 (대기열 토큰 필요O)

```mermaid
sequenceDiagram
    participant 사용자
    participant Payment service  
    participant Reservation service
    participant Point service
    participant Seat service
    participant Redis
    participant DB
    사용자->>Payment service: POST /api/v1/payments
    Note over 사용자, Payment service: [reservaitonId:"res-123", userId:"user-123"]
    
    Payment service->>DB: 트랜잭션 시작
    Payment service->>Reservation service: 예약 내역 확인
    Reservation service->>DB: 예약 정보 조회 (FOR UPDATE)
    DB-->>Reservation service: 예약 정보 반환
    Reservation service->>Reservation service: 예약 상태 유효성 검사
    Note right of Reservation service: 예약자, 임시 배정 상태, 만료 시간

    alt 유효한 임시 배정
    Payment service->>Point service: 잔액 확인
    Point service->>DB: 잔액 조회 및 잔액 차감(FOR UPDATE)
    Note over Point service, DB: balance - seatPrice
    DB-->>Point service: 차감 후 현재 잔액 반환
    Point service->>DB: 결제 이력 저장
    
    Payment service->>Reservation service: 예약 확정 처리
    Reservation service->>DB: 예약 상태 업데이트
    Note over Reservation service, DB: status: TEMPORARILY_ASSIGNED -> CONFIRMED <br> confirmedAt: now
    
    Payment service->>Seat service: 좌석 확정 처리
    Seat service->>DB: 좌석 상태 업데이트
    Note over Seat service, DB: status: TEMPORARILY_ASSIGNED -> RESERVED <br> reservedAt: now
    
    Payment service->>DB:트랜잭션 커밋
    DB-->>Payment service: 결제 완료
    Payment service-->>사용자: 결제 성공
    Note over Payment service, 사용자: paymentId: "pay-123" <br> status:COMPLETED <br> price:50000
    end
```
