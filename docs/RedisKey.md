## Redis 데이터 구조
### 1. 대기열 토큰 관리

#### 1-1. 토큰 정보 저장
```
Key : queue:token:{token_uuid}
Value : JSON string
TTL : 1800 초 

SET "queue:token:550e8400-e29b-41d4-a716-446655440000" 
    "{\"user_id\":\"user-123\",\"queuePosition\":150, \"estimatedWaitTimeMinutes\":50, \"status\":\"WAITING\",\"issued_at\":\"2025-05-29T15:20:00Z\",\"expiresAt\":\"2025-05-29T15:25:00Z\"}" 
    EX 3600
```

#### 1-2. 대기열 순서 관리 (Sorted Set)
```
Key : queue:waiting
Score : timestamp (요청 시간)
Memeber : user_id

ZADD "queue:waiting" 1704067200 "user-123"
ZADD "queue:waiting" 1704067201 "user-456"
```

#### 1-3. 활성 사용자 관리 (Set)
```
Key : queue:active
Members : 현재 활성 상태인 user_id들

SADD "queue:active" "user-123"
EXPIRE "queue:active:user-123" 1800 # 30분 후 개별 만료
```

### 2. 분산 락

#### 2-1. 대기열 진입용
```
Key : queue:lock
Value : userId
TTL : 5초

SET "queue:lock" "user-123" EX 5 NX
```

