# TradeUp 개발 로드맵

현재 상태는 **Phase 1.5 구현 완료·최종 문서화 및 정리 중**이다. 아래 Phase 0·1은 당시 완료 기록이며, 현재 기술 버전·스키마·실행 절차는 [README](../README.md)와 [개발환경](development-environment.md)을 따른다. 이후 Phase는 모두 계획이며 구현 성과와 구분한다.

## Phase 0. 프로젝트 기반 정의 ✅

**Status: Completed**

프로젝트 목표, MVP 범위, 핵심 도메인과 개발 원칙을 정의했다. Java 25, 초기 Spring Boot 4.1.0, PostgreSQL 18, Docker Compose 기반의 로컬 개발환경을 구성했다. 초기 선택의 이유와 대안은 [ADR-0001](adr/0001-initial-technology-stack.md)에 보존한다.

## Phase 1. 기본 백엔드 구현 ✅

**Status: Completed**

- Member, Product, Wishlist, Trade의 기본 기능과 비즈니스 규칙 구현
- 당시 Flyway V1~V4 기반 PostgreSQL 스키마 관리
- Product/Trade 도메인 단위 테스트 및 TradeService Mockito 단위 테스트
- Member, Product, Wishlist, Trade의 실제 PostgreSQL 기반 Service 통합 테스트
- Trade API의 MockMvc 통합 테스트

Phase 1은 정상적인 순차 요청 흐름을 보장하는 수준으로 완료했다. 동시 요청에 대한 정합성 문제는 Phase 3에서 다룬다.

## Phase 1.5. 인증·인가 및 API 기반 정비

**Status: 구현 완료·최종 문서화 및 정리 중**

### 구현 완료 사항

- BCrypt 비밀번호 해싱, DB 자격 증명 검증, 로그인 API
- Flyway V5의 password_hash 추가와 기존 null 해시 회원의 로그인 거부
- Spring Security와 JWT Access Token 기반 stateless 인증
- 인증된 회원 ID를 principal로 사용하는 리소스 소유권·본인·판매자 검사
- 요청 DTO Validation, BusinessException, GlobalExceptionHandler와 ProblemDetail
- JSON·본문 누락·경로 변환 오류 처리 및 JWT 필터 401 경로 구분
- PATCH의 필드 미전달·명시적 null·빈 문자열 구분, 자료형·값·알 수 없는 필드 검증
- springdoc-openapi 3.2.0과 Swagger UI, Bearer 인증 및 공개 API 예외 표시
- 자체 JWT에 불필요한 기본 사용자 자동 설정만 제외
- 단위·실제 PostgreSQL·MockMvc·거래 트랜잭션 회귀 검증

현재 Boot는 4.1.2이며 정상 HTTP 응답은 생성·삭제를 포함해 모두 200이다. 오류 상태는 정비했지만 생성 201·삭제 204로의 전환은 적용하지 않았다. 업무 API 17개(공개 4개·보호 13개)의 계약은 [API 명세](api-design.md)를 따른다.

최근 검증 근거는 2026-10-29(KST)의 기존 JUnit XML 30개 suite이며 총 462개 성공·실패 0·스킵 0이다. 이번 문서 정리에서는 테스트를 재실행하지 않았다. 대부분의 통합 테스트는 롤백하지만 거래 트랜잭션 검증은 실제 커밋 후 직접 정리한다. 상세 환경과 근거는 [개발환경](development-environment.md)을 참고한다.

### 최종 정리·확인 사항

- README·요구사항·도메인·DB·API·기술 결정 문서 간 정합성 검토
- 최종 변경 파일과 기존 검증 결과의 기준 시점 확인
- 실제 Swagger 생성 문서와 로그인·Authorize·보호 API 호출 흐름 확인
- 임시 보안 의존성 override의 근거 보완 및 제거 조건 관리

현재 Swagger 문서 경로는 공개다. 운영 환경의 비활성화·접근 제한과 비밀키 주입·교체 정책은 후속 운영 권고이며 구현 완료로 표시하지 않는다. Refresh Token·로그아웃·토큰 폐기, 검색·페이징도 현재 미구현이다.

## Phase 2. 데이터베이스 성능 개선

**Status: Planned**

대량 데이터를 구성하고 조회 성능 문제를 재현한 뒤 원인을 분석하고 개선한다. N+1 발생 여부와 쿼리 수·응답 시간을 측정하고, 조회 방식·인덱스 등의 개선 전후를 비교한다. 아직 측정 결과나 최적화 완료 성과는 없다.

## Phase 3. 동시성 및 트랜잭션

**Status: Planned**

동시에 발생하는 거래 요청·수락과 상품 수정 등의 경쟁 조건을 재현하고 정합성을 검증한다. 관찰한 문제를 근거로 잠금·버전 관리 등 제어 방식을 선택한다. 기존 순차 요청의 커밋·롤백 테스트와 동시성 보장은 구분한다.

## Phase 4. 캐시 및 비동기 처리

**Status: Planned**

Redis 캐시와 비동기 처리의 필요성을 측정하고 트래픽 증가에 대응하도록 확장한다. 캐시 무효화와 비동기 실패 처리도 함께 검토한다. 현재 Redis·비동기 처리는 도입하지 않았다.

## Phase 5. 운영 환경 구축

**Status: Planned**

AWS 배포, 모니터링과 운영 환경을 구성한다. 운영 비밀키 관리, Swagger 공개 제한, 환경별 설정 정책을 정리하고 검증한다. 현재 AWS 배포·운영 검증이 완료된 상태는 아니다.

## Phase 6. 부하 테스트 및 확장

**Status: Planned**

부하 테스트로 병목과 자원 사용을 측정하고 애플리케이션·인프라를 개선한다. 현재 462개 기능 테스트 성공을 처리량·지연시간·운영 용량의 증거로 사용하지 않는다.

## Phase 7. AI-native Backend

**Status: Planned**

기존 TradeUp 백엔드 API를 Tool로 활용하여 LLM이 자연어 요청을 이해하고 백엔드 기능을 호출하도록 확장한다. 현재 AI Agent·Tool Calling 기능은 미구현이다.

각 단계에서 문제 상황, 재현 조건, 대안, 선택 이유, 검증 결과와 한계를 기록한다. 기술 선택 원칙은 [기술 결정](tech-decisions.md)을 따른다.



