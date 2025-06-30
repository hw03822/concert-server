## 데이터베이스 ERD 설계 (Entity Relationship Diagram)
## 개요
콘서트 예약 서비스의 데이터 베이스 설계 문서입니다. MySQL과 Redis를 혼합하여 사용합니다. 

#### 데이터베이스 구성
- MySQL : 영구 데이터 저장 (사용자, 콘서트, 좌석, 포인트 내역, 예약, 결제)
- Redis : 임시 데이터 및 캐시 (대기열, 분산락, 캐시)

## 전체 ERD
![콘서트 예매 서비스 (2)](https://github.com/user-attachments/assets/5a3b5283-0197-45ac-9ce3-9bc4b82b0b4e)

## MySQL 테이블 설계
### 1. users (사용자)
사용자 기본 정보를 관리하는 테이블

| 컬럼명     | 데이터 타입   | 제약조건    | 기본값                        | 설명         |
|------------|----------------|-------------|-------------------------------|--------------|
| user_id    | VARCHAR        | PRIMARY KEY | -                             | 사용자 ID    |
| balance    | DECIMAL(15,2)  | NOT NULL    | 0                             | 현재 잔액 (원) |
| created_at | TIMESTAMP      | NOT NULL    | CURRENT_TIMESTAMP             | 생성일시     |
| updated_at | TIMESTAMP      | NOT NULL    | CURRENT_TIMESTAMP ON UPDATE   | 수정일시     |

#### DDL
```
CREATE TABLE users (
    user_id VARCHAR(50) PRIMARY KEY,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_balance CHECK (balance >= 0)
);
```

### 2. balance_history (잔액 거래 히스토리)
사용자 잔액 변동 내역을 관리하는 테이블

| 컬럼명        | 데이터 타입    | 제약조건    | 기본값 | 설명         |
|---------------|----------------|-------------|--------|--------------|
| history_id    | VARCHAR        | PRIMARY KEY | -      | 히스토리 ID   |
| user_id       | VARCHAR        | FOREIGN KEY | -      | 사용자 ID     |
| transaction_type | ENUM           | NOT NULL    | -      | 거래 구분 (charge, payment, refund) |
| amount        | DECIMAL(15,2)  | NOT NULL    | -      | 거래 금액     |
| current_balance | DECIMAL(15,2) | NOT NULL    | -      | 거래 후 잔액 |
| created_at    | TIMESTAMP      | NOT NULL    | CURRENT_TIMESTAMP | 생성일시 |

#### trnasaction_type ENUM
- `CHARGE` : 잔액 충전
- `PAYMENT` : 결제
- `REFUND` : 환불

#### DDL
```
CREATE TABLE balance_history (
    history_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    transaction_type ENUM('CHARGE', 'PAYMENT', 'REFUND') NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    current_balance DECIMAL(15, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    
    CONSTRAINT chk_balance_after CHECK (current_balance >= 0)
);
```

### 3. concerts (콘서트)
콘서트 기본 정보를 관리하는 테이블

| 컬럼명     | 데이터 타입 | 제약조건    | 기본값 | 설명       |
|------------|-------------|-------------|--------|------------|
| concert_id | BIGINT      | PRIMARY KEY AUTO_INCREMENT | -      | 콘서트 ID  |
| title      | VARCHAR     | NOT NULL    | -      | 콘서트 이름 |
| artist     | VARCHAR     | NOT NULL    | -      | 아티스트 이름 |
| concert_at | TIMESTAMP   | NOT NULL    | -      | 콘서트 일시 |
| create_at  | TIMESTAMP   | NOT NULL    | CURRENT_TIMESTAMP | 생성일시 |

#### DDL
```
CREATE TABLE concerts (
    concert_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    artist VARCHAR(255) NOT NULL,
    concert_at TIMESTAMP NOT NULL,
    create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### 4. seats (좌석)
콘서트별 좌석 정보 및 상태를 관리하는 테이블

| 컬럼명      | 데이터 타입   | 제약조건    | 기본값 | 설명                         |
|-------------|----------------|-------------|--------|------------------------------|
| seat_id     | BIGINT         | PRIMARY KEY AUTO_INCREMENT | -      | 좌석 ID      |
| concert_id  | BIGINT         | FOREIGN KEY | -      | 콘서트 ID                    |
| seat_number | INT            | NOT NULL    | -      | 좌석 번호                    |
| price       | DECIMAL(15,2)  | NOT NULL    | -      | 좌석 가격                    |
| status      | ENUM           | NOT NULL    | AVAILABLE | 상태 (AVAILABLE, TEMPORARILY_ASSIGNED, RESERVED) |
| reserved_at | TIMESTAMP      |             | -      | 예약 만료 시간 정보 |

#### status ENUM
- `AVAILABLE` : 예약 가능
- `TEMPORARILY_ASSIGNED` : 임시 배정 (5분간)
- `RESERVED` : 예약 완료

#### DDL
```
CREATE TABLE seats (
    seat_id BIGINT PRIMARY KEY AUTO_INCREMENT ,
    concert_id BIGINT NOT NULL,
    seat_number INT NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    status ENUM('AVAILABLE', 'TEMPORARILY_ASSIGNED', 'RESERVED') NOT NULL DEFAULT 'AVAILABLE',
    reserved_at TIMESTAMP,

    FOREIGN KEY (concert_id) REFERENCES concerts(concert_id),
    
    CONSTRAINT chk_seat_number CHECK (seat_number BETWEEN 1 AND 50),
    CONSTRAINT chk_price CHECK (price > 0)
);
```

### 5. reservations (예약)
좌석 예약 정보를 관리하는 테이블

| 컬럼명        | 데이터 타입    | 제약조건    | 기본값 | 설명                          |
|---------------|----------------|-------------|--------|-------------------------------|
| reservation_id| VARCHAR        | PRIMARY KEY | -      | 예약 ID                       |
| user_id       | VARCHAR        | FOREIGN KEY | -      | 사용자 ID                     |
| seat_id       | BIGINT         | FOREIGN KEY | -      | 좌석 ID                       |
| concert_id    | BIGINT         | FOREIGN KEY | -      | 콘서트 ID                     |
| status        | ENUM           | NOT NULL    | TEMPORARILY_ASSIGNED | 상태 (TEMPORARILY_ASSIGNED, CONFIRMED, CANCELLED, EXPIRED) |
| confirmed_at  | TIMESTAMP      |             | -      | 예약 확정 일시                |
| expired_at    | TIMESTAMP      | NOT NULL    | -      | 예약 만료 일시                |
| title         | VARCHAR        | NOT NULL    | -      | 콘서트 제목 (중복 방지용 스냅샷) |
| concert_date  | TIMESTAMP      | NOT NULL    | -      | 콘서트 일시 (스냅샷)          |
| price         | DECIMAL(15,2)  | NOT NULL    | -      | 좌석 가격 (스냅샷)            |
| seat_number   | INT            | NOT NULL    | -      | 좌석 번호 (스냅샷)            |
| created_at    | TIMESTAMP      | NOT NULL    | CURRENT_TIMESTAMP | 생성일시     |

#### status ENUM
- `TEMPORARILY_ASSIGNED` : 임시 배정 중
- `CONFIRMED` : 결제 완료로 확정
- `CANCELLED` : 사용자 취소
- 'EXPIRED' : 시간 만료로 자동 취소

#### DDL
```
CREATE TABLE reservations (
    reservation_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    seat_id BIGINT NOT NULL,
    concert_id BIGINT NOT NULL,
    status ENUM('TEMPORARILY_ASSIGNED', 'CONFIRMED', 'CANCELLED', 'EXPIRED') NOT NULL DEFAULT 'TEMPORARILY_ASSIGNED',
    confirmed_at TIMESTAMP,
    expired_at TIMESTAMP NOT NULL,
    title VARCHAR(255) NOT NULL,
    concert_date TIMESTAMP NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    seat_number INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (seat_id) REFERENCES seats(seat_id),
    FOREIGN KEY (concert_id) REFERENCES concerts(concert_id),
    
    CONSTRAINT chk_reserv_price CHECK (price > 0),
    CONSTRAINT chk_expires_at CHECK (expired_at > created_at)
);
```

### 6. payments (결제)
결제 정보를 관리하는 테이블

| 컬럼명       | 데이터 타입   | 제약조건    | 기본값 | 설명                                 |
|--------------|----------------|-------------|--------|--------------------------------------|
| payment_id   | VARCHAR        | PRIMARY KEY | -      | 결제 ID                              |
| reservation_id | VARCHAR      | FOREIGN KEY | -      | 예약 ID                              |
| user_id      | VARCHAR        | FOREIGN KEY | -      | 사용자 ID                            |
| price        | DECIMAL(15,2)  | NOT NULL    | -      | 결제 금액                            |
| status       | ENUM           | NOT NULL    | COMPLETED | 상태 (COMPLETED, FAILED, CANCELLED) |
| created_at   | TIMESTAMP      | NOT NULL    | CURRENT_TIMESTAMP | 생성일시                     |

#### status ENUM
- `COMPLETED` : 결제 완료
- `FAILED` : 결제 실패
- `CANCELLED` : 결제 취소

#### DDL
```
CREATE TABLE payments (
    payment_id VARCHAR(50) PRIMARY KEY,
    reservation_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    status ENUM('COMPLETED', 'FAILED', 'CANCELLED') NOT NULL DEFAULT 'COMPLETED' NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    
     CONSTRAINT chk_payment_price CHECK (price > 0)
);
```
