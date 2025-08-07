## 🎫 콘서트 예약 서비스

### ✅ 개요
- 대기열 시스템을 통해 안전한 콘서트 좌석 예약 서비스를 제공하며, 동시 접속 상황에서도 작업가능한 유저만 예약을 수행하는 서비스

### 🛠️ 개발 환경
| Layer | Tech |
|-------|------|
| Language | Java 17 |
| Framework | Spring Boot 3|
| DB | MySQL |
| Caching | Redis |
| Messaging | Kafka |
| Build Tool | Gradle |
| Infra | Docker, Docker Compose |
| Test | JUnit5, Mockito |

### ⚙️ 주요 기능
**1. 대기열 시스템**
   - 서비스 이용을 위한 대기열 토큰 발급
   - Redis 기반 토큰 큐를 이용한 입장 순서 제어
   - 토큰을 이용해 대기열 검증을 통과해야 모든 API 이용 가능

**2. 콘서트 정보 조회**
   - 예약 가능한 콘서트 날짜 목록 조회
   - 특정 날짜의 예약 가능한 좌석 정보 조회

**3. 좌석 예약**
   - 좌석에 대한 예약 요청 처리
   - 결제 전 좌석 임시 배정 (5분 경과 시 자동 해제)
   - 임시 배정된 좌석 수동 취소 시 즉시 다른 사용자 예약 가능한 상태로 전환

**4. 잔액 충전**
   - 결제에 사용할 잔액 충전
   - 사용자 식별자를 통해 해당 사용자의 잔액 조회

**5. 결제 처리**
   - 임시 배정된 좌석에 대한 결제 처리
   - 결제 완료 시 좌석 배정 및 토큰 만료

6. [빠른 매진 랭킹 기능](https://github.com/hw03822/concert-server/blob/main/docs/콘서트매진랭킹설계.md)
   - 빠른 매진 랭킹을 Redis 기반으로 처리

### 📝 설계 문서
- [시퀀스 다이어그램](https://github.com/hw03822/concert-server/blob/main/docs/sequence.md) 
- [ERD](https://github.com/hw03822/concert-server/blob/main/docs/erd.md)
- [인프라 구성도](https://github.com/hw03822/concert-server/blob/main/docs/infra.md)

### 📝 그 외 문서
- [동시성 문제 설계](https://github.com/hw03822/concert-server/blob/main/docs/concurrencyIssue.md)
- [캐시 적용 설계](https://github.com/hw03822/concert-server/blob/main/docs/Cache.md)
