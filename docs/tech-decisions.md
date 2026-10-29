# TradeUp 기술 선택 원칙과 현재 구현 결정

## 1. 기본 원칙

TradeUp에서는 기술 자체를 사용해보는 것을 목표로 하지 않는다. 먼저 문제를 정의하고, 해당 문제를 해결하기 위해 필요한 기술을 선택한다.

새로운 기술을 도입할 때는 다음 질문에 답할 수 있어야 한다.

1. 현재 어떤 문제가 발생했는가?
2. 기존 방식으로 해결하기 어려운 이유는 무엇인가?
3. 왜 이 기술을 선택했는가?
4. 다른 대안은 무엇이 있었는가?
5. 기술 도입 후 어떤 점이 개선되었는가?
6. 새롭게 발생한 복잡성이나 단점은 무엇인가?

## 2. 초기 결정과 현재 구성의 구분

초기 애플리케이션은 단일 애플리케이션으로 단순하게 시작하고, 실제 문제를 재현한 뒤 필요성이 확인될 때 추가 기술을 도입한다. Redis, 메시지 브로커, 검색 엔진, 컨테이너 오케스트레이션을 초기부터 도입하지 않는 방향을 유지한다.

최초 기술 선택은 [ADR-0001](adr/0001-initial-technology-stack.md)에 기록되어 있다. Java 25, Spring Boot 4.1.0, Spring Data JPA, PostgreSQL 18, Gradle Kotlin DSL, Docker Compose라는 **당시 결정은 변경하지 않는다.**

현재는 Spring Boot 4.1.2, JJWT 0.13.0, springdoc-openapi 3.2.0 등을 사용한다. 현재 버전의 기준은 [build.gradle.kts](../build.gradle.kts), 실행 절차는 [개발환경](development-environment.md)이다. 애플리케이션 코드는 Java이며 Kotlin DSL은 빌드 설정에 사용한다.

## 3. Phase 1.5에서 확인되는 구현 선택

아래는 **현재 코드와 테스트에서 확인되는 선택, 해결하는 문제와 보장 범위**를 정리한 것이다. 당시 수행한 대안 비교·벤치마크·도입 경위를 사후에 추정한 기록은 아니다. 구체적인 HTTP 계약은 [API 명세](api-design.md)에 둔다.

### 3.1. BCrypt 비밀번호 보호

- 선택: PasswordEncoder 빈으로 BCryptPasswordEncoder를 사용하고, MemberService의 가입 경로에서 해시를 생성하며 로그인에서 matches로 비교한다.
- 해결하는 문제: 평문 비밀번호를 회원 데이터에 저장하지 않고 자격 증명을 확인한다. 회원·로그인 응답 DTO는 비밀번호·해시를 제외한다.
- 근거: [PasswordEncoderConfig](../src/main/java/com/tradeup/backend/member/config/PasswordEncoderConfig.java), [MemberService](../src/main/java/com/tradeup/backend/member/service/MemberService.java), [회원 API 테스트](../src/test/java/com/tradeup/backend/member/controller/MemberControllerIntegrationTest.java), [로그인 API 테스트](../src/test/java/com/tradeup/backend/member/controller/AuthControllerIntegrationTest.java).
- 한계: V5의 기존 null 해시 회원은 로그인 거부로 처리한다. 비밀번호 재설정·기존 데이터 보완 기능과 비밀번호 길이·복잡도에 대한 별도 DTO 제약은 없다.

### 3.2. JWT 인증과 리소스 인가의 분리

- 선택: 자체 JwtTokenProvider가 HS256 Access Token을 발급·검증하고 JwtAuthenticationFilter가 회원 ID를 principal로 설정한다. 필터 체인은 stateless이며 Controller는 인증된 ID를 받는다.
- 해결하는 문제: 클라이언트가 body/query의 주체 ID를 바꾸어 다른 회원으로 행동하는 것을 방지한다. Controller·Service는 인증 후 경로의 본인 여부나 리소스의 판매자·소유자 여부를 별도로 검사한다.
- 근거: [JWT provider](../src/main/java/com/tradeup/backend/auth/jwt/JwtTokenProvider.java), [JWT 필터](../src/main/java/com/tradeup/backend/auth/jwt/JwtAuthenticationFilter.java), [SecurityConfig](../src/main/java/com/tradeup/backend/auth/config/SecurityConfig.java), [인증 테스트](../src/test/java/com/tradeup/backend/auth/jwt/JwtAuthenticationIntegrationTest.java), [상품 API 테스트](../src/test/java/com/tradeup/backend/product/controller/ProductControllerIntegrationTest.java).
- 한계: 토큰 검증 자체가 DB 회원 존재 확인이나 모든 리소스의 접근 허용을 뜻하지 않는다. Refresh Token·로그아웃·토큰 폐기는 없으며, 현재 만료 설정은 3,600초이다.

자체 로그인은 DB 회원과 BCrypt 비교로 처리하므로 기본 메모리 사용자를 쓰지 않는다. [애플리케이션 설정](../src/main/java/com/tradeup/backend/TradeupBackendApplication.java)은 UserDetailsServiceAutoConfiguration만 제외하고 SecurityFilterChain은 유지한다.

JWT 키는 TRADEUP_JWT_SECRET_BASE64 환경변수에서 읽으며 Base64 디코딩 결과 최소 32바이트를 요구하고 기본키 fallback은 없다. 운영 비밀 저장·주입·교체 정책은 로컬 환경변수 사용과 별도의 후속 운영 과제다. 실제 키·JWT·개인정보는 문서에 기록하지 않는다.

### 3.3. 입력 검증과 비즈니스 오류 계약

- 선택: 일반 요청 DTO에는 Bean Validation과 Controller의 @Valid를 적용하고, 비즈니스 규칙 위반은 BusinessException과 ErrorCode로 표현한다. GlobalExceptionHandler는 ProblemDetail과 code를 반환한다.
- 해결하는 문제: 입력 제약과 업무 상태·권한 위반을 구분하고 400·401·403·404·409를 명시적인 API 계약으로 제공한다.
- 근거: [가입 DTO](../src/main/java/com/tradeup/backend/member/dto/MemberCreateRequest.java), [상품 등록 DTO](../src/main/java/com/tradeup/backend/product/dto/ProductCreateRequest.java), [GlobalExceptionHandler](../src/main/java/com/tradeup/backend/global/exception/GlobalExceptionHandler.java), [Validation 테스트](../src/test/java/com/tradeup/backend/global/validation/BeanValidationIntegrationTest.java), [MVC 입력 오류 테스트](../src/test/java/com/tradeup/backend/global/validation/MvcInputErrorIntegrationTest.java).
- 한계: Bean Validation은 errors 배열을 추가하지만 비즈니스·JSON·경로 오류는 그렇지 않다. JWT 필터·entry point의 401은 sendError 경로이고, 로그인 실패의 401 ProblemDetail과 다르다. 일반 예외 전체를 INTERNAL_ERROR로 바꾸는 핸들러는 없다.

현재 정상 응답은 생성·삭제를 포함해 모두 200이다. 오류 상태 정비와 정상 응답을 201·204로 변경하는 일은 구분하며, 후자는 적용하지 않았다.

### 3.4. PATCH에서 필드 존재와 값의 분리

- 선택: Controller가 Jackson 3 JsonNode를 받고 ProductUpdateRequest.from이 존재·자료형·값을 검사한다. Service는 DTO를 Entity에 전달하고 Entity는 Present 플래그에 따라 요청한 필드만 변경한다.
- 해결하는 문제: 미전달과 명시적 null을 구분한다. 설명 null은 삭제, 빈 문자열은 저장, 미전달은 유지하며 가격 0도 허용한다. 빈 객체·알 수 없는 필드·잘못된 자료형·범위는 거부한다.
- 근거: [ProductUpdateRequest](../src/main/java/com/tradeup/backend/product/dto/ProductUpdateRequest.java), [ProductService](../src/main/java/com/tradeup/backend/product/service/ProductService.java), [Product](../src/main/java/com/tradeup/backend/product/domain/Product.java), [DTO 테스트](../src/test/java/com/tradeup/backend/product/dto/ProductUpdateRequestTest.java), [API·DB 검증](../src/test/java/com/tradeup/backend/product/controller/ProductControllerIntegrationTest.java).
- 한계: public record 생성자와 Product.update 자체가 입력값 전체를 재검증하지 않는다. 현재 HTTP 경로는 from을 거치지만 내부 직접 호출까지 모든 값 불변식이 보호된다고 보장하지 않는다. 이 엄격한 JSON 규칙을 다른 DTO API에 일반화하지 않는다.

### 3.5. JPA 트랜잭션과 DB 마이그레이션

- 선택: Flyway V1–V5가 스키마를 관리하고 ddl-auto는 none이다. 상품 수정은 쓰기 트랜잭션의 Dirty Checking을 사용하며 거래·상품 상태 전이는 같은 Service 트랜잭션으로 처리한다.
- 해결하는 문제: 스키마 변경을 SQL 이력으로 관리하고 관련 상태 변경의 커밋·롤백을 함께 검증한다. 상품 삭제 시 거래 이력을 보존하고, 삭제 가능한 상품의 찜만 먼저 정리한다.
- 근거: [마이그레이션](../src/main/resources/db/migration), [DB 설계](database-design.md), [TradeService](../src/main/java/com/tradeup/backend/trade/service/TradeService.java), [거래 트랜잭션 테스트](../src/test/java/com/tradeup/backend/trade/service/TradeTransactionIntegrationTest.java).
- 한계: 기존 테스트가 순차 요청의 커밋·롤백을 검증하더라도 동시 요청 정합성이 확보된 것은 아니다. 활성 REQUESTED의 동시 중복 제어와 잠금·버전 관리는 후속 과제다.

### 3.6. Swagger/OpenAPI와 실제 인증 정책의 구분

- 선택: springdoc-openapi 3.2.0의 WebMVC UI starter와 OpenApiConfig를 사용한다. 전역 bearerAuth를 선언하고 공개 업무 API 4개는 빈 @SecurityRequirements로 해제한다.
- 해결하는 문제: API를 탐색하고 로그인으로 받은 accessToken으로 보호 API를 호출하는 진입점을 제공한다. 자물쇠 표시와 실제 SecurityConfig의 공개·보호 정책을 맞춘다.
- 근거: [OpenApiConfig](../src/main/java/com/tradeup/backend/global/config/OpenApiConfig.java), [AuthController](../src/main/java/com/tradeup/backend/member/controller/AuthController.java), [MemberController](../src/main/java/com/tradeup/backend/member/controller/MemberController.java), [ProductController](../src/main/java/com/tradeup/backend/product/controller/ProductController.java).
- 한계: 문서 어노테이션은 실제 인가를 강제하지 않는다. 현재 Swagger 경로는 공개이며 운영 비활성화·접근 제한은 권고이지 구현 완료가 아니다. 생성된 PATCH 스키마·공통 오류 문서와 실제 호출 흐름은 별도 확인 대상이다.

### 3.7. 테스트 근거와 환경의 한계

단위 테스트, 실제 PostgreSQL Service 통합 테스트, MockMvc 인증·권한·API 회귀 테스트를 사용한다. 기존 XML 기준 2026-10-29(KST) 462개 성공이며 이번 문서 작업에서 재실행하지 않았다. 상세 근거는 [개발환경](development-environment.md)을 따른다.

대부분의 통합 테스트는 롤백하지만 거래 트랜잭션 검증은 실제 커밋 후 직접 정리한다. [임의 JWT 키 초기화기](../src/test/java/com/tradeup/backend/support/RandomJwtSecretInitializer.java)는 테스트 컨텍스트별 키를 제공한다. 기본 DB는 로컬 애플리케이션과 공유하며 Testcontainers·테스트 전용 DB 분리는 현재 적용하지 않았다.

## 4. 임시 보안 의존성 override

현재 빌드에서 확인한 값은 다음과 같다.

| Boot 버전 속성 | 설정 값 |
|---|---|
| logback.version | 1.6.6 |
| jackson-2-bom.version | 2.21.8 |
| jackson-bom.version | 3.1.8 |

build.gradle.kts의 주석은 이를 임시 보안 override로 설명하고, **Spring Boot가 수정 버전을 관리하면 제거**하도록 적고 있다. Jackson 2와 3은 별도의 BOM 속성으로 관리한다.

2026-10-29 문서 정리 시점에 이 override는 미커밋 작업트리에 포함되어 있었고, 비교한 기존 문서·커밋 이력에는 구체적인 CVE, 해당 버전 선택 과정, 변경 전후 의존성 해석·호환성 검증의 상세 기록이 없었다. 이 문서는 이를 추정하여 채우지 않는다. 기존 462개 테스트 성공을 모든 보안 취약점 제거 또는 모든 라이브러리 조합의 호환성 보장으로 해석하지 않는다.

향후 유지보수 시 공식 근거·실제 선택 버전·검증 결과를 남기고, Boot가 필요한 수정 버전을 관리하는지 확인한 뒤 override 제거 여부를 결정한다. 단순 Boot 버전 증가만으로 제거 조건이 충족됐다고 판단하지 않는다.

## 5. 기술 도입 기록과 후속 계획

중요한 결정은 Problem, Alternatives, Decision, Reason, Result, Trade-off를 남긴다. 실제 비교 기록이나 측정 근거가 없으면 확인되지 않았음을 명시한다. 이 문서는 현재 확인되는 구현 선택을 모으고, 별도 아키텍처 결정 기록이 필요한 경우에만 새 ADR로 확장한다.

기존 ADR-0001은 최초 기술 선택의 기록으로 보존한다. 현재 버전 변경 때문에 과거의 Decision을 현재 값으로 덮어쓰지 않는다.

N+1 최적화·동시성 제어·Redis·비동기·AWS·부하 테스트·AI Agent는 [로드맵](roadmap.md)의 후속 계획이다. 기술 도입 후에는 기능 테스트뿐 아니라 해당 문제에 맞는 측정·재현 결과와 새롭게 발생한 한계를 함께 기록한다.



