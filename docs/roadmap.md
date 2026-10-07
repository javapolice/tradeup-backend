# TradeUp 개발 로드맵



## Phase 0. 프로젝트 기반 정의 ✅



프로젝트 목표, MVP 범위, 핵심 도메인, 개발 원칙을 정의하고 
Java25, Spring Boot 4.1, PostgreSQL 18, Docker Compose 기반의
로컬 개발환경을 구성한다.

**Status: Completed**



## Phase 1. 기본 백엔드 구현 ✅



Spring Boot와 JPA를 기반으로 TradeUp의 기본 기능을 구현한다.

**Status: Completed**

- Member, Product, Wishlist, Trade의 기본 기능과 비즈니스 규칙 구현
- Flyway V1~V4 기반 PostgreSQL 스키마 관리
- Product/Trade 도메인 단위 테스트 및 TradeService Mockito 단위 테스트
- Member, Product, Wishlist, Trade의 실제 PostgreSQL 기반 Service 통합 테스트
- Trade API의 MockMvc 통합 테스트

Phase 1은 정상적인 순차 요청 흐름을 보장하는 수준으로 완료했으며, 동시 요청에 대한 정합성 문제는 Phase 3에서 다룬다.

## Phase 1.5. API 기반 정비 검토

Security/JWT, 요청 값 Validation, Global Exception Handler 및 공통 오류 응답, HTTP 응답 상태 개선을 검토한다. 현재 구현 완료된 기능이 아니다.



## Phase 2. 데이터베이스 성능 개선



대량 데이터를 구성하고 조회 성능 문제를 재현한 뒤 원인을 분석하고 개선한다.



## Phase 3. 동시성 및 트랜잭션



동시에 발생하는 거래 요청을 통해 데이터 정합성 문제를 재현하고 해결한다.



## Phase 4. 캐시 및 비동기 처리



Redis와 비동기 처리 등을 도입하여 트래픽 증가에 대응할 수 있도록 확장한다.



## Phase 5. 운영 환경 구축



AWS에 애플리케이션을 배포하고 모니터링 및 운영 환경을 구성한다.



## Phase 6. 부하 테스트 및 확장



부하 테스트를 통해 병목 구간을 찾고 애플리케이션과 인프라를 개선한다.



## Phase 7. AI-native Backend



기존 TradeUp 백엔드 API를 Tool로 활용하여 LLM이 사용자의 자연어 요청을 이해하고 필요한 백엔드 기능을 호출할 수 있도록 확장한다.



