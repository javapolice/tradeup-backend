# Development Environment

TradeUp의 로컬 실행·테스트 절차를 정리한다. 프로젝트는 Phase 1.5 구현 완료·최종 정리 중이며, API 계약은 [HTTP API](api-design.md), 프로젝트 소개는 [README](../README.md)를 기준으로 한다.

## 1. 준비 환경

| 항목 | 현재 기준 |
|---|---|
| JDK | Java 25, 로컬 개발 기준 Eclipse Temurin |
| 애플리케이션 | Spring Boot 4.1.2, Spring MVC, Spring Data JPA |
| 보안·검증 | Spring Security, BCrypt, JJWT 0.13.0, Jakarta Bean Validation |
| 빌드 | Gradle Wrapper 9.5.1, Kotlin DSL, dependency-management 플러그인 1.1.7 |
| DB | PostgreSQL 18, Docker Compose |
| API 문서 | springdoc-openapi 3.2.0 |
| 도구 | Git, Docker 및 Compose. IDE는 선택 사항이며 IntelliJ IDEA 등을 사용할 수 있다. |

명령은 프로젝트 루트에서 실행한다. Windows는 PowerShell, macOS/Linux는 bash 또는 zsh 기준이다. JDK가 설치되어 있고 `java`가 Java 25를 가리키는지 확인한다.

```shell
java -version
docker compose version
```

별도 Gradle 설치 대신 저장소의 wrapper를 사용한다. macOS/Linux에서는 현재 저장소의 `gradlew`에 실행 권한을 부여해야 한다.

```shell
chmod +x gradlew
./gradlew --version
```

Windows PowerShell에서는 다음 명령으로 확인한다.

```powershell
.\gradlew.bat --version
```

빌드 기준은 [build.gradle.kts](../build.gradle.kts)와 [wrapper 설정](../gradle/wrapper/gradle-wrapper.properties)이다. 최초 빌드에는 Gradle·의존성 다운로드를 위한 네트워크 접근이 필요하다.

## 2. PostgreSQL 실행

로컬 머신에 PostgreSQL을 직접 설치하는 대신 [compose.yml](../compose.yml)의 PostgreSQL 18 컨테이너를 사용한다. Docker 엔진을 시작한 뒤 다음 명령을 실행한다.

```shell
docker compose up -d
docker compose ps
```

컨테이너가 실행 중인지 확인하고 DB 초기화 완료 후 애플리케이션을 시작한다. 필요한 경우 다음 로그로 준비 상태를 확인한다.

```shell
docker compose logs postgres
```

- 기본 연결: `jdbc:postgresql://localhost:5432/tradeup`
- 호스트 포트: `5432`. 다른 PostgreSQL이 점유하고 있으면 먼저 충돌을 해결한다.
- DB 연결 설정은 [application.yaml](../src/main/resources/application.yaml), 로컬 컨테이너 설정은 compose 파일을 기준으로 한다. 로컬 기본 자격 증명을 운영 환경에 재사용하지 않는다.
- 데이터는 Docker named volume에 유지된다.
- 애플리케이션 시작 시 Flyway V1–V5로 스키마를 관리한다. Hibernate의 `ddl-auto`는 `none`, `open-in-view`는 `false`이다.

## 3. JWT 환경변수 설정

애플리케이션을 실행할 셸에 `TRADEUP_JWT_SECRET_BASE64`를 설정한다. `jwt.secret-base64`가 이 변수를 참조하며 기본 비밀키 fallback은 없다.

- 값은 **암호학적으로 안전하게 생성한 임의 바이트의 Base64 문자열**이어야 한다.
- Base64 디코딩 결과는 **최소 32바이트**여야 한다. 문자열 길이가 32자라는 뜻이 아니다.
- 아래 명령은 32바이트 키를 생성해 현재 셸 환경변수에 저장하며 키를 화면에 출력하지 않는다.
- 실제 키·JWT·사용자 비밀번호를 문서, Git, 로그, 스크린샷에 남기지 않는다.

### Windows PowerShell

Windows PowerShell과 PowerShell 7에서 사용할 수 있도록 난수 생성기의 인스턴스 API를 사용한다.

```powershell
$jwtKeyBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtRandom.GetBytes($jwtKeyBytes)
    $env:TRADEUP_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtKeyBytes)
} finally {
    $jwtRandom.Dispose()
    [Array]::Clear($jwtKeyBytes, 0, $jwtKeyBytes.Length)
}
```

### macOS/Linux

OpenSSL이 설치된 환경에서 실행한다.

```shell
export TRADEUP_JWT_SECRET_BASE64="$(openssl rand -base64 32)"
```

환경변수는 해당 셸에서 시작한 자식 프로세스에 전달된다. 새 터미널이나 IDE 실행에는 자동으로 공유되지 않으므로 IDE에서는 Run Configuration의 환경변수에 별도로 설정한다. `.env` 자동 로딩은 현재 구성에서 확인되지 않았으므로 `.env` 파일 생성만으로 적용된다고 가정하지 않는다.

재시작할 때마다 키를 새로 생성하면 이전 키로 발급한 JWT는 검증되지 않는다. 로컬에서 기존 토큰을 계속 사용하려면 같은 키를 안전하게 유지해야 한다. 현재 Access Token 유효기간 설정은 3,600초이다.

## 4. 애플리케이션 실행

PostgreSQL 준비와 JWT 변수 설정을 마친 **같은 셸**에서 실행한다.

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

macOS/Linux:

```shell
./gradlew bootRun
```

기본 HTTP 포트는 `8080`이다. 이 포트를 다른 프로세스가 점유하지 않았는지 확인한다. 서버는 `Ctrl+C`로 종료한다. 자체 JWT 구조에서는 기본 메모리 사용자가 필요하지 않아 `UserDetailsServiceAutoConfiguration`만 제외하며, Spring Security 필터 체인은 유지한다.

## 5. Swagger로 API 사용

애플리케이션 시작 후 [Swagger UI](http://localhost:8080/swagger-ui.html)를 연다. 명세는 [OpenAPI JSON](http://localhost:8080/v3/api-docs)과 [OpenAPI YAML](http://localhost:8080/v3/api-docs.yaml)로도 제공한다.

1. `POST /api/members`에서 테스트용 이메일·닉네임·비밀번호로 가입한다. 실제 개인정보나 다른 서비스의 비밀번호를 사용하지 않는다.
2. `POST /api/auth/login`을 펼치고 **Try it out**으로 가입한 자격 증명을 보내 실행한다.
3. 200 응답의 `accessToken`을 복사한다. `tokenType`은 `Bearer`이다.
4. UI 상단 **Authorize**의 `bearerAuth` 입력란에 **토큰 값만** 입력한다. `Bearer ` 접두사는 UI가 붙이므로 함께 입력하지 않는다.
5. 예를 들어 `POST /api/products`를 호출한다. 판매자는 JWT principal로 결정되며 sellerId를 요청 본문에 넣지 않는다.
6. 본인·판매자·소유자 조건이 있는 API는 해당 회원의 토큰으로 호출한다. 자물쇠 표시는 인증 요구를 뜻하며 모든 리소스에 대한 접근 권한을 보장하지 않는다.

회원가입·로그인·상품 목록·상품 단건 조회 4개는 공개이며 나머지 업무 API 13개에는 JWT가 필요하다. 전체 요청 필드·PATCH 규칙·오류 응답 차이는 [HTTP API](api-design.md)를 따른다. 현재 정상 응답은 생성·삭제를 포함해 모두 200이며, 삭제 응답 본문은 비어 있다.

현재 SecurityConfig는 Swagger UI·JSON·YAML 경로를 프로필 구분 없이 공개한다. **운영 환경에서는 문서를 비활성화하거나 인증·네트워크 접근 제한을 적용할 것을 권장한다. 운영 제한은 아직 구현되지 않았다.** 운영 JWT 키는 별도의 비밀정보 주입·관리 체계로 보호해야 하며, 이 로컬 실행 절차를 운영 구성으로 간주하지 않는다.

## 6. 테스트 실행과 검증 근거

테스트도 기본 설정의 실제 PostgreSQL에 연결하므로 DB가 먼저 실행되어야 한다. `RandomJwtSecretInitializer`가 Spring 테스트 컨텍스트별 임의 키를 제공하므로 `test`에는 JWT 환경변수 수동 설정이 필요하지 않다. 이는 `bootRun`의 키 설정 요구와 다르다.

Windows PowerShell:

```powershell
.\gradlew.bat clean test
```

macOS/Linux:

```shell
./gradlew clean test
```

단위 테스트는 도메인·DTO·Service 등을, MockMvc와 Service 통합 테스트는 인증·인가·Validation·오류 계약·PATCH·실제 DB 반영 등을 검증한다. 대부분의 통합 테스트는 `@Transactional`로 롤백한다. `TradeTransactionIntegrationTest`는 서비스 자체의 커밋·롤백을 확인하기 위해 실제 커밋 후 `@AfterEach`에서 생성 데이터를 직접 정리한다. 모든 테스트가 자동 롤백되는 것은 아니다.

기본 연결 대상이 로컬 애플리케이션 DB와 같으므로 보존해야 할 데이터가 있는 운영·공유 DB를 대상으로 실행하지 않는다. 테스트 전용 DB 분리와 Testcontainers는 현재 적용되어 있지 않다.

최근 확인 결과는 **2026-10-29(KST) 총 462개 성공, 실패 0, 오류 0, 스킵 0**이다. 기존 `build/test-results/test/TEST-*.xml` 30개 suite를 집계했으며, 결과 파일의 최근 갱신 시각은 같은 날 19:19(KST)이다. 이번 문서 갱신에서는 테스트를 재실행하지 않았다. 이 결과는 성능·부하·동시성 보장을 의미하지 않는다.

실행 후 HTML 보고서는 `build/reports/tests/test/index.html`, JUnit XML은 `build/test-results/test/`에서 확인한다. `clean`은 이전 보고서를 제거하므로 검증 근거를 갱신할 때 명령·시점·환경을 함께 기록한다.

## 7. 자주 확인할 문제

| 증상 | 확인 사항 |
|---|---|
| DB 연결·Flyway 초기화 실패 | Docker 엔진, 컨테이너 준비 상태, 5432 포트, application.yaml과 compose 설정 |
| JWT 설정으로 기동 실패 | 같은 셸의 변수 설정 여부, 유효한 Base64인지, 디코딩 후 최소 32바이트인지. 키 자체는 출력하지 않음 |
| macOS/Linux의 wrapper 실행 권한 오류 | 프로젝트 루트에서 `chmod +x gradlew` 적용 |
| Java toolchain·실행 버전 문제 | `java -version`, wrapper의 `--version`, IDE JDK 설정이 Java 25인지 확인 |
| 보호 API 401 | Authorize에 토큰만 입력했는지, 만료·서명키 변경 여부 확인 |
| 공개 API도 401 | 잘못된 Bearer 토큰을 제공하면 공개 API도 필터에서 거부됨. Authorize의 토큰을 해제하고 재시도 |
| 정상 토큰으로 403 | 경로의 회원 ID 또는 상품·찜·거래의 소유권과 JWT 회원이 일치하는지 확인 |

## 8. 로컬 인프라 종료

애플리케이션 종료 후 DB를 정지·제거하려면 다음 명령을 사용한다. named volume의 데이터는 유지된다.

```shell
docker compose down
```

`docker compose down -v`는 DB 볼륨과 데이터를 삭제한다. 로컬 데이터를 완전히 초기화하려는 경우에만 사용한다.

## 9. 의존성 관리와 후속 범위

현재 build.gradle.kts는 임시 보안 override로 Logback 1.6.6, Jackson 2 BOM 2.21.8, Jackson 3 BOM 3.1.8을 지정한다. 주석상 제거 조건은 Spring Boot가 수정 버전을 관리하게 되는 시점이다. 구체적인 CVE·선택 이유·호환성 검증 이력은 별도 근거 없이 추정하지 않는다. Jackson 2는 일부 의존성이 사용하며 애플리케이션의 Jackson 3과 구분한다.

N+1 재현·최적화, 동시성 제어, Redis 캐시, 비동기 처리, AWS 배포, 부하 테스트, AI Agent 연계는 후속 계획이다. 현재 실행에 Redis·AWS·LLM 계정은 필요하지 않다. 상세 계획은 [로드맵](roadmap.md), 기술 선택 원칙은 [기술 결정](tech-decisions.md)을 참고한다.

