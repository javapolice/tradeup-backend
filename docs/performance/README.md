# Phase 2 성능 테스트 안내

## 목적과 현재 상태

Phase 2는 상품 목록 조회의 N+1을 재현하고, 측정 근거를 남긴 뒤 최적화 전후를 비교하는 과정이다.
현재 Step 2-3까지 독립 PostgreSQL 테스트 환경, 목록의 판매자 닉네임 추가, 상품 1/10/100개 SQL 측정을 완료했다.
Step 2-4에서는 이 안내를 추가하고 Controller 테스트 실행 경로를 독립 PostgreSQL 기반 원본 클래스로 단일화했다. 응답시간 측정, Fetch Join, 인덱스 최적화는 아직 수행하지 않았다.

## 재현 코드와 호출 흐름

실험 진입점은 [ProductNicknameNPlusOneIntegrationTest](../../src/test/java/com/tradeup/backend/performance/ProductNicknameNPlusOneIntegrationTest.java)의
`measuresNicknameReadsInsideTheServiceTransaction(int)`이다.

```text
MockMvc GET /api/products
  → ProductController.getSellingProducts()
  → ProductService.getSellingProducts() [readOnly 트랜잭션]
  → ProductRepository.findAllByStatus(SELLING)
  → ProductListResponse.from(product)
  → product.getSeller().getNickname() [LAZY 판매자 조회]
```

[ProductService](../../src/main/java/com/tradeup/backend/product/service/ProductService.java)는 조회한 상품을 트랜잭션 내부에서 DTO로 변환한다.
[ProductListResponse](../../src/main/java/com/tradeup/backend/product/dto/ProductListResponse.java)는 기존 목록 필드에 `sellerNickname`을 추가한다.
OSIV=false이므로 서비스 트랜잭션 종료 후 지연 로딩에 의존하지 않도록 DTO 변환을 서비스 안에서 끝낸다.
현재 쿼리는 Fetch Join 없이 판매중 상품을 조회하므로, 서로 다른 판매자의 닉네임 접근이 추가 SELECT를 만든다.
등록·상세·수정 응답은 기존 `ProductResponse` 계약을 유지한다.

## 테스트 역할과 활성 여부

아래 링크의 클래스명은 파일명과 같다. 활성은 코드상 실행 가능 상태이며 최신 통과를 의미하지 않는다.

| 클래스 | 상태 | 역할 |
|---|---|---|
| [PostgresIsolationSmokeTest](../../src/test/java/com/tradeup/backend/performance/PostgresIsolationSmokeTest.java) | 활성 | 독립 DB 연결, PostgreSQL 18, Flyway V1~V5와 스키마 확인 |
| [ProductNicknameNPlusOneIntegrationTest](../../src/test/java/com/tradeup/backend/performance/ProductNicknameNPlusOneIntegrationTest.java) | 활성 | 새 목록 계약·N+1과 트랜잭션 경계, 기존 CRUD 응답의 정확한 필드·삭제 빈 본문 확인 |
| ProductQueryPerformanceIntegrationTest | 과거 실험 기록으로 보존 | `@Disabled`였던 테스트 소스는 제거. [Step 2-2 결과](step2-2-product-baseline.md)와 [삭제 전 소스 원문](archive/ProductQueryPerformanceIntegrationTest.java.txt)으로 보존 |
| [ProductControllerIntegrationTest](../../src/test/java/com/tradeup/backend/product/controller/ProductControllerIntegrationTest.java) | 활성·독립 Testcontainers DB | IsolatedPostgresTest를 직접 상속. 기존 18개 테스트 메서드·72개 호출의 CRUD, PATCH 입력, 인증·소유권, 오류 응답과 데이터 보존 확인 |
| [ProductServiceTest](../../src/test/java/com/tradeup/backend/product/service/ProductServiceTest.java) | 활성·DB 없음 | Mockito로 서비스 검사 순서와 실패 후 후속 호출 차단 확인 |
| [ProductServiceIntegrationTest](../../src/test/java/com/tradeup/backend/product/service/ProductServiceIntegrationTest.java) | 활성·독립 DB 강제 없음 | 실제 상품·찜 삭제와 거래 이력 존재 시 데이터 보존 확인 |

기반 클래스 [IsolatedPostgresTest](../../src/test/java/com/tradeup/backend/performance/support/IsolatedPostgresTest.java)는 DB 수명과 연결 보호를 담당한다.
[MeasuredSqlInspector](../../src/test/java/com/tradeup/backend/performance/support/MeasuredSqlInspector.java)는 `start()`~`stop()` 사이 현재 스레드의 Hibernate SQL을 수집한다.
N+1 테스트는 Statistics·SQL 로그와 이를 교차 확인하고, 임시 repository AOP로 DTO 변환 전 판매자 프록시와 읽기 전용 트랜잭션을 관찰한다.

## 개발 DB와 격리 DB, 안전한 실행

- 기본 [application.yaml](../../src/main/resources/application.yaml)은 `localhost:5432/tradeup`을 가리킨다. ProductControllerIntegrationTest는 이를 독립 DB 설정으로 덮어쓴다. ProductServiceIntegrationTest에는 독립 DB 강제가 없으며, 롤백만으로 연결 대상 격리를 보장하지 않는다.
- 격리 테스트는 Testcontainers의 `postgres:18`, DB `tradeup_performance`, 임의 비밀번호와 비5432 호스트 포트를 사용한다. DataSource·Hikari·Flyway를 같은 컨테이너로 지정하고 Bean 초기화 전 설정·실행 상태를 검증한다. 기동·검증 실패 시 중단하고 컨텍스트 종료 시 컨테이너를 정리한다.
- 성능 fixture는 별도 `TransactionTemplate`에서 flush·clear 후 커밋한다. HTTP 요청에는 외부 테스트 트랜잭션이 없으며, 측정 직전 Statistics를 초기화하고 Inspector 수집을 시작한다. 2차·쿼리 캐시는 비활성화한다.
- Controller 테스트의 기존 `@Transactional`은 유지하며 각 호출의 fixture 변경은 기본 롤백으로 정리한다. 클래스 종료 시 상속한 `@DirtiesContext(AFTER_CLASS)`가 캐시에서 컨텍스트를 제거·종료하고 등록된 컨테이너 중지 처리를 실행한다.
- 아래처럼 정확한 클래스만 선택한다. 전체 `test`나 넓은 패턴은 개발 DB를 사용하는 테스트까지 선택할 수 있다. 중복 wrapper인 IsolatedProductControllerIntegrationTest는 원본의 격리 실행 검증 후 제거했다.

프로젝트 루트에서 JDK 25와 실행 가능한 Docker 환경을 준비한 뒤 사용한다.

```powershell
.\gradlew.bat test --tests com.tradeup.backend.product.controller.ProductControllerIntegrationTest
```

2026-11-17 KST에 위 원본 클래스만 선택해 72회 성공, 실패·오류·스킵 0회를 확인했다.
Bean 초기화 전 격리 검증, Flyway와 Hibernate 로그의 연결 대상은 모두 `localhost:51217/tradeup_performance`였으며 PostgreSQL 18.6을 사용했다.
Hikari 종료 로그와 실행 후 Docker 목록으로 임시 PostgreSQL·Ryuk 컨테이너 정리를 확인했다. 포트는 실행마다 달라진다.
이 결과는 이번 Controller 실행에 한정되며 서비스·N+1 테스트나 전체 테스트의 재검증 결과가 아니다.

## 과거 실험 기록

아래는 **2026-10-30 당시 main / HEAD f8cf77c와 미커밋 Phase 2 코드에서 기록된 결과**다. 현재 작업 트리의 재실행 결과가 아니다.
각 상품은 서로 다른 판매자를 참조했고, fixture·Flyway·정리 작업은 SQL 측정 범위에서 제외했다.

| 상품 수 | 닉네임 추가 전 SELECT | 닉네임 추가 후 SELECT |
|---:|---:|---:|
| 1 | 1 | 2 |
| 10 | 1 | 11 |
| 100 | 1 | 101 |

이 차이는 응답 계약 변경 전후의 기록이며 최적화 효과가 아니다. 판매자를 공유하면 영속성 컨텍스트 때문에 추가 조회 수가 줄어들 수 있다.

- [Step 2-2 원본 기록](step2-2-product-baseline.md): 당시 환경·SQL·3회 통과 기록. 삭제한 과거 테스트의 당시 결과다.
- [Step 2-3 원본 기록](step2-3-product-n-plus-one.md): 닉네임 N+1 SQL과 당시 선택 실행 89회 통과 기록. 전체 테스트 통과를 뜻하지 않는다.

Step 2-3 기록의 IsolatedProductControllerIntegrationTest 명칭과 실행 명령은 당시 구조를 보존한 것이다. 현재 Controller 실행에는 위 원본 클래스 명령을 사용한다.

ProductQueryPerformanceIntegrationTest는 현재 응답·서비스 반환 계약에 맞지 않아 비활성 상태에서 정리했다. 다른 소스 코드의 참조가 없음을 확인하고 삭제 전 파일을 바이트 단위로 동일하게 보존했다.
Step 2-2 말미의 테스트 소스 보존·비활성 설명과 실행 명령은 삭제 전 상태의 기록이다. 현재 소스 원문은 위 `.java.txt` 보관 파일에 있으며 테스트 소스로 컴파일하거나 실행하지 않는다.

상세 수치·환경·SQL과 실행 경위는 위 원본을 기준으로 확인한다. 현재 계측은 동기 MockMvc 요청의 Hibernate SQL을 대상으로 하며 응답시간·처리량·부하를 측정하지 않는다.

## 다음 작업 — 미수행

1. 동일 fixture에 워밍업 후 MockMvc 요청을 반복해 응답시간 평균·중앙값(p50)·p95를 기록한다. 표본 수·워밍업·캐시·계측 조건을 함께 남긴다. 실제 네트워크 시간은 포함하지 않는다.
2. Fetch Join 적용 전후 동일 데이터·조건에서 응답 계약과 SQL 수·응답시간을 비교한다. 현재 N+1 재현 assertion은 최적화 후 기대값과 구분하고 기존 기록을 보존한다.
3. 조회 실행 계획과 데이터 규모를 확인한 뒤 인덱스 필요성을 판단하고 전후를 비교한다. 인덱스 작업과 N+1 해소는 각각 검증한다.
