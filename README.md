# TradeUp

대규모 트래픽과 실제 운영 이슈를 단계적으로 재현하고 해결하며, 최종적으로 AI Agent 기능까지 확장하는 중고거래 플랫폼 백엔드 프로젝트입니다.

서비스 기능을 많이 구현하는 것보다 문제를 재현하고, 원인을 분석하고, 해결 과정을 검증하는 것을 목표로 합니다. 현재는 인증·권한과 API 계약을 갖춘 기본 백엔드를 구현했으며, 성능·동시성 개선은 이후 단계에서 실험합니다.

## Current Phase

**Phase 1.5 — 구현 완료·최종 문서화 및 정리 중**

Member, Product, Wishlist, Trade의 기본 기능에 인증·인가·입력 검증·오류 처리·Swagger/OpenAPI를 적용했습니다. 업무 API는 총 17개로, 공개 4개와 JWT 인증 필수 13개입니다. 상세 계약은 [HTTP API](docs/api-design.md)를 참고하세요.

## 핵심 구현과 검증

| 구현 | 기술적 선택과 검증 내용 |
|---|---|
| BCrypt 회원 인증 | 비밀번호를 해시로 저장하고 로그인 시 비교합니다. 응답에서 비밀번호·해시가 제외되는지 검증합니다. |
| Spring Security + JWT | HS256 Access Token과 stateless 인증을 사용합니다. 누락·만료·위조·변조 토큰 거부를 검증합니다. |
| 인증 회원 ID 기반 인가 | 요청 body/query의 주체 ID 대신 JWT principal을 사용합니다. 본인·판매자·소유자 검사와 타인 접근 거부를 검증합니다. |
| Validation·예외 처리 | DTO 제약, JSON·경로 오류, BusinessException과 ProblemDetail을 적용합니다. JWT 필터의 401 응답은 별도 경로로 구분합니다. |
| 상품 PATCH | 미전달 필드를 유지하고 설명의 명시적 null과 빈 문자열을 구분합니다. 잘못된 값 거부와 DB 반영·보존을 검증합니다. |
| 거래·데이터 정합성 | 상태 전이, 거래 이력 보존, 상품 삭제와 찜 정리, 서비스 트랜잭션의 커밋·롤백을 검증합니다. 동시 요청 보장은 후속 과제입니다. |
| Swagger/OpenAPI 3 | Bearer 인증 스키마를 정의하고 공개 API의 인증 요구를 해제했습니다. UI에서 로그인 후 보호 API를 호출할 수 있습니다. |

## Tech Stack

| 영역 | 현재 구성 |
|---|---|
| 언어·빌드 | Java 25 (LTS), Gradle Wrapper 9.5.1, Kotlin DSL |
| 애플리케이션 | Spring Boot 4.1.2, Spring MVC, Spring Data JPA |
| 인증·검증 | Spring Security, BCrypt, JJWT 0.13.0, Jakarta Bean Validation |
| 데이터·직렬화 | PostgreSQL 18, Flyway V1–V5, Jackson 3 및 일부 의존성의 Jackson 2 |
| API 문서 | springdoc-openapi 3.2.0 |
| 로컬 인프라·테스트 | Docker Compose, JUnit, Mockito, MockMvc, 실제 PostgreSQL 통합 테스트 |

버전의 기준은 [빌드 설정](build.gradle.kts)입니다. 현재 임시 보안 버전 override와 상세 실행 조건은 [개발환경](docs/development-environment.md)에 정리했습니다.

## Local Development

Java 25, Docker와 Docker Compose가 필요합니다. 프로젝트 루트에서 다음 순서로 진행합니다.

1. `docker compose up -d`로 PostgreSQL을 실행합니다.
2. [JWT 환경변수 설정](docs/development-environment.md#3-jwt-환경변수-설정)에 따라 `TRADEUP_JWT_SECRET_BASE64`를 설정합니다. Base64 디코딩 결과가 최소 32바이트여야 합니다.
3. 같은 셸에서 아래 명령으로 애플리케이션을 실행합니다.
4. [Swagger UI](http://localhost:8080/swagger-ui.html)에서 회원가입 → 로그인 → accessToken 발급 → Authorize → 보호 API 호출 순서로 사용합니다.

| 환경 | 애플리케이션 실행 | 전체 테스트 |
|---|---|---|
| Windows PowerShell | `.\gradlew.bat bootRun` | `.\gradlew.bat clean test` |
| macOS/Linux | `./gradlew bootRun` | `./gradlew clean test` |

macOS/Linux에서는 먼저 `chmod +x gradlew`로 실행 권한을 부여합니다. 테스트도 PostgreSQL이 필요하며, 기존 테스트 초기화기가 임의 JWT 키를 제공하므로 테스트용 키를 수동 설정할 필요는 없습니다. 상세 절차·오류 해결·종료 방법은 [개발환경](docs/development-environment.md)을 참고하세요.

현재 Swagger 문서 경로는 공개되어 있습니다. 운영 환경에서는 비활성화 또는 접근 제한이 필요하다는 권고이며, 운영 제한이 이미 구현된 상태는 아닙니다.

## Tests

**최근 검증: 2026-10-29(KST), 총 462개 성공·실패 0·스킵 0.** 근거는 당시 생성된 `build/test-results/test/TEST-*.xml` 30개 suite의 집계입니다. 이번 문서 정리에서는 테스트를 재실행하지 않았습니다.

도메인·DTO·Service 단위 테스트, 실제 PostgreSQL Service 통합 테스트, MockMvc 인증·권한·API 회귀 테스트를 포함합니다. 대부분의 통합 테스트는 테스트 트랜잭션을 롤백하지만, 거래 트랜잭션 검증은 실제 커밋 후 데이터를 직접 정리합니다. 성능·부하·동시성 검증 완료를 의미하지는 않습니다.

## 향후 계획

N+1 재현·조회 최적화 → 동시성 제어 → Redis 캐시·비동기 처리 → AWS 배포·운영 → 부하 테스트·확장 → AI Agent 연계를 계획하고 있습니다. 모두 현재 구현 성과와 구분되는 후속 작업이며, 문제와 측정 결과를 근거로 기술을 도입합니다. 상세 범위는 [로드맵](docs/roadmap.md)을 참고하세요.

## Documentation

* [MVP Requirements](docs/requirements.md)
* [Domain Model](docs/domain-model.md)
* [Database Design](docs/database-design.md)
* [HTTP API](docs/api-design.md)
* [Development Roadmap](docs/roadmap.md)
* [Technology Decisions](docs/tech-decisions.md)
* [Development Environment](docs/development-environment.md)
* [Architecture Decision Records](docs/adr)

