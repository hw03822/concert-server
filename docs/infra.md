### 인프라 구성도
![image](https://github.com/user-attachments/assets/a1f47c7e-245f-47f2-ac0c-f5e8886479d0)

- HTTPS(443)를 사용하여 호출 
- 소규모 트래픽으로 가정 후 클라이언트로 들어오는 모든 트래픽을 한 대의 서버로 처리 (추후 Load Balancer 로 확장해 트래픽 분산 예정)
- 대기열 관리, 좌석 상태 관리, 분산 락을 활용해 동시성 제어를 위해 Redis 사용
