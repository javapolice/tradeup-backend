# TradeUp HTTP API

## 1. 현재 구현 기준

Phase 1.5의 현재 Controller, DTO, Service, SecurityConfig 및 관련 테스트를 기준으로 HTTP 계약을 정리한다. 업무 API는 총 17개이며 공개 API 4개와 JWT 인증 필수 API 13개로 구분한다.

- 요청 본문이 있는 API는 `Content-Type: application/json`을 사용한다. 본문이 없는 API는 각 표에 명시한다.
- `{memberId}`, `{productId}`, `{tradeId}`, `{wishlistId}`는 `Long`으로 변환되는 path variable이다. 경로 ID 자체에는 `@Positive` 제약이 없으며, 변환할 수 없는 문자열이나 Long 범위 초과 값은 400이다.
- 현재 업무 API에는 필수 query parameter가 없다.
- 아래 응답 이름은 Entity가 아닌 응답 DTO를 뜻한다. 객체·배열 정상 응답은 JSON이며 별도 `data` 래퍼가 없다.

### 인증 및 요청자 식별

보호 API에는 다음 헤더가 필요하다. 실제 토큰 값은 문서에 기록하지 않는다.

```http
Authorization: Bearer <accessToken>
```

`JwtAuthenticationFilter`가 토큰을 검증한 뒤 회원 ID를 SecurityContext의 principal에 저장하고, Controller는 `@AuthenticationPrincipal Long authenticatedMemberId`로 이를 받는다. 상품 판매자, 찜 등록자, 거래 구매자 및 상태 변경 요청자의 ID는 이 principal에서 결정된다. 클라이언트가 body/query의 `sellerId`, `buyerId`, `memberId`로 인증 주체를 지정하지 않는다.

회원 조회·찜 목록·구매 내역의 경로 `memberId`는 조회 대상이다. Controller가 principal과 같은지 검사하며, 다른 회원이면 403으로 거부한다. 응답의 `sellerId`, `buyerId`, `memberId`는 리소스의 소유·참여 관계를 나타낸다.

- 공개 API는 회원가입, 로그인, 상품 목록, 상품 단건 조회이다.
- 그 외 업무 API는 JWT 인증 후 본인·판매자·소유자 등 각 API의 권한 검사를 수행한다. JWT 인증이 모든 리소스에 대한 접근을 허용하는 것은 아니다.
- 토큰이 없거나 헤더가 `Bearer `로 시작하지 않으면 JWT 필터는 인증을 설정하지 않는다. 보호 API는 401, 공개 API는 익명 접근이 가능하다.
- `Bearer ` 토큰이 잘못되었거나 만료되면 공개 API에서도 필터가 401을 반환한다.
- 인증은 stateless 방식이다. 로그인으로 발급한 Access Token을 후속 요청에 전달하며, Refresh Token·로그아웃·토큰 폐기 API는 없다.

### HTTP 응답

- 아래 API는 성공 시 모두 `200 OK`를 반환한다. 생성 API에 `201 Created`를 적용하지 않았다.
- Product/Wishlist 삭제는 `200 OK`와 빈 본문을 반환하며 `204 No Content`를 적용하지 않았다.
- 목록 응답은 JSON 배열이며 페이징 응답은 없다.
- 목록이 비어 있으면 `200 OK`와 빈 배열 `[]`을 반환한다. 정렬 순서는 별도로 보장하지 않는다.
- 공통 입력 오류는 400, 인증 실패는 401이다. API별 주요 비즈니스 오류는 각 표에 정리하며, 상세 응답 형식은 6절을 따른다.

## 2. Member API

| 기능 | Method / 경로 | 인증·권한 | JSON 요청 본문 | 성공 응답 | 주요 비즈니스 오류 |
|---|---|---|---|---|---|
| 회원 가입 | `POST /api/members` | 공개 | `email`, `nickname`, `password` | 200, MemberResponse | 409: 이메일·닉네임 중복 |
| 로그인 | `POST /api/auth/login` | 공개 | `email`, `password` | 200, LoginResponse | 401: 자격 증명 불일치 |
| 회원 조회 | `GET /api/members/{memberId}` | JWT, 본인 | 없음 | 200, MemberResponse | 403: 타인 조회 / 404: 본인 ID의 회원 없음 |

### 요청 필드

| DTO | 필드 | 검증 |
|---|---|---|
| MemberCreateRequest | `email` | 필수, null·빈 문자열·공백만 있는 값 불가, 이메일 형식, 최대 255자 |
| MemberCreateRequest | `nickname` | 필수, null·빈 문자열·공백만 있는 값 불가, 최대 255자 |
| MemberCreateRequest | `password` | 필수, null·빈 문자열·공백만 있는 값 불가 |
| LoginRequest | `email`, `password` | 각각 필수, null·빈 문자열·공백만 있는 값 불가 |

로그인 email에는 회원가입과 달리 `@Email`·`@Size` 제약이 없다. 비어 있지 않은 값은 Service에서 자격 증명을 검사한다. 현재 DTO에는 비밀번호 길이·복잡도에 대한 별도 제약이 없다.

가입 비밀번호는 BCrypt 해시로 저장한다. 로그인에서 존재하지 않는 이메일, 잘못된 비밀번호, 비밀번호 해시가 null인 기존 회원은 모두 401 `UNAUTHORIZED`와 동일한 detail `이메일 또는 비밀번호가 올바르지 않습니다.`를 반환한다.

### 응답 필드

| DTO | 필드 | 의미 |
|---|---|---|
| MemberResponse | `id` (Long), `email` (문자열), `nickname` (문자열) | 회원 정보. 비밀번호·해시 제외 |
| LoginResponse | `accessToken` (문자열), `tokenType` (문자열) | 발급된 JWT와 `Bearer`. 회원 정보·비밀번호·해시 제외 |

Access Token은 HS256으로 서명하며 subject에 회원 ID를 담는다. 현재 설정의 유효기간은 3,600초이며 LoginResponse에는 별도 만료 시각 필드가 없다.

## 3. Product API

| 기능 | Method / 경로 | 인증·권한 | JSON 요청 본문 | 성공 응답 | 주요 비즈니스 오류 |
|---|---|---|---|---|---|
| 상품 등록 | `POST /api/products` | JWT | `title`, `description`, `price` | 200, ProductResponse | 404: JWT 회원에 해당하는 판매자 없음 |
| 상품 단건 조회 | `GET /api/products/{productId}` | 공개 | 없음 | 200, ProductResponse | 404: 상품 없음 |
| 판매 중 상품 목록 | `GET /api/products` | 공개 | 없음 | 200, ProductResponse 배열 | 없음 |
| 상품 수정 | `PATCH /api/products/{productId}` | JWT, 판매자 | `title`, `description`, `price` 중 1개 이상 | 200, ProductResponse | 403: 비판매자 / 404: 상품 없음 / 409: SELLING 아님 |
| 상품 삭제 | `DELETE /api/products/{productId}` | JWT, 판매자 | 없음 | 200, 빈 본문 | 403: 비판매자 / 404: 상품 없음 / 409: SELLING 아님 또는 거래 이력 존재 |

ProductResponse 필드: `id` (Long), `sellerId` (Long), `title` (문자열), `description` (문자열 또는 null), `price` (Long), `status` (`SELLING`, `RESERVED`, `SOLD`).

### 상품 등록

ProductCreateRequest의 `title`은 필수이며 null·빈 문자열·공백만 있는 값은 불가하고 최대 255자이다. `description`은 선택이며 생략·null·빈 문자열을 허용하고 문자열 길이는 최대 255자이다. `price`는 필수 Long 값이며 null·음수는 불가하고 0원을 허용한다.

판매자는 principal로 결정하며 최초 상태는 `SELLING`이다. 목록은 SELLING 상품만 반환하고, 단건 조회에는 판매 상태 제한이 없다. 조건 검색·페이징 API는 미구현이다.

### 상품 부분 수정(PATCH)

Controller는 Jackson 3 `JsonNode`를 받고 `ProductUpdateRequest.from(...)`에서 필드 존재 여부와 자료형·값을 검사한다. 내부 `titlePresent`, `descriptionPresent`, `pricePresent`는 요청 필드가 아니다.

| 필드 | 미전달 | 명시적 null | 빈 문자열 | 전달 값 검증 |
|---|---|---|---|---|
| `title` | 기존 제목 유지 | 400 | 400 | JSON 문자열, 공백만 있는 값 불가, 최대 255자 |
| `description` | 기존 설명 유지 | 설명을 null로 변경 | 빈 문자열 저장 | JSON 문자열 또는 null, 문자열은 최대 255자 |
| `price` | 기존 가격 유지 | 400 | 400 | JSON 정수, 0 이상 9,223,372,036,854,775,807 이하 |

- 본문은 객체여야 하며 최소 한 필드를 포함해야 한다. `{}`, JSON null·배열·숫자·문자열·boolean은 400이다.
- 허용 필드는 `title`, `description`, `price`뿐이다. `sellerId`, `status`, 내부 Present 플래그를 포함한 알 수 없는 필드는 400이다.
- title·description의 숫자·boolean·객체·배열은 문자열로 변환하지 않고 거부한다. description만 null을 허용한다.
- price의 음수, 소수(`1.5`뿐 아니라 `1.0`), 지수 표기(`1e3`), 문자열(`"1000"`), boolean·객체·배열, Long 범위 초과는 400이다.
- title·description 값은 trim하지 않고 그대로 반영한다. 설명의 공백 문자열도 허용한다. 길이 검사는 Java `String.length()` 기준이다.
- 여러 필드를 동시에 수정할 수 있다. 요청하지 않은 필드와 판매자·상태는 유지한다.
- 본문 누락·잘못된 JSON 구문은 400이며, 의미상 잘못된 PATCH 값과는 detail이 다르다(6절).

예를 들어 `{"title":"변경 제목"}`은 제목만 수정하고, `{"description":null}`은 설명만 삭제한다. `{"description":"","price":0}`은 빈 설명과 0원을 함께 저장한다.

이 엄격한 자료형·알 수 없는 필드 거부 정책은 PATCH의 수동 검증 규칙이다. 다른 DTO 기반 API까지 같은 역직렬화 규칙이라고 일반화하지 않는다.

### 상품 수정·삭제 권한과 이력 보존

인증 후 상품을 조회하고 판매자를 검사하며, 비판매자는 상태 검사보다 먼저 403으로 거부한다. 수정·삭제는 판매자 본인의 SELLING 상품에만 가능하다. 삭제는 Trade 상태와 관계없이 거래 이력이 하나라도 있으면 거부한다. 거래 이력이 없으면 같은 트랜잭션에서 관련 Wishlist를 먼저 삭제하고 Product를 삭제한다. Trade는 함께 삭제하지 않는다.

상세 삭제 정책은 [요구사항](requirements.md)의 상품 삭제 절을 따른다.

## 4. Wishlist API

| 기능 | Method / 경로 | 인증·권한 | JSON 요청 본문 | 성공 응답 | 주요 비즈니스 오류 |
|---|---|---|---|---|---|
| 상품 찜 | `POST /api/wishlists` | JWT | `productId` | 200, WishlistResponse | 404: 회원·상품 없음 / 409: 자기 상품 또는 중복 찜 |
| 상품 찜 취소 | `DELETE /api/wishlists/{wishlistId}` | JWT, 찜 소유자 | 없음 | 200, 빈 본문 | 403: 타인의 찜 / 404: 찜 없음 |
| 회원 찜 목록 | `GET /api/members/{memberId}/wishlists` | JWT, 본인 | 없음 | 200, WishlistResponse 배열 | 403: 타인 조회 |

WishlistCreateRequest의 `productId`는 필수 Long이며 null·0·음수는 불가하다. 등록 회원은 principal로 결정한다. 자신의 상품과 동일 회원·상품의 중복 찜은 거부하며, 현재 찜 등록에는 상품 판매 상태 제한이 없다.

WishlistResponse 필드: `id`, `memberId`, `productId`(모두 Long). 목록은 Wishlist 관계 DTO 배열이며 Product 상세 객체 배열이 아니다. 본인 ID의 목록 조회는 회원 존재 여부를 별도 조회하지 않으며 결과가 없으면 빈 배열이다.

## 5. Trade API

| 기능 | Method / 경로 | 인증·권한 | JSON 요청 본문 | 성공 응답 | 주요 비즈니스 오류 |
|---|---|---|---|---|---|
| 거래 요청 | `POST /api/trades` | JWT | `productId` | 200, TradeResponse | 404: 회원·상품 없음 / 409: 자기 상품, SELLING 아님, 중복 REQUESTED |
| 거래 수락 | `POST /api/trades/{tradeId}/accept` | JWT, 판매자 | 없음 | 200, TradeResponse | 403: 비판매자 / 404: 거래 없음 / 409: REQUESTED 아님 또는 상품이 SELLING 아님 |
| 거래 거절 | `POST /api/trades/{tradeId}/reject` | JWT, 판매자 | 없음 | 200, TradeResponse | 403: 비판매자 / 404: 거래 없음 / 409: REQUESTED 아님 |
| 거래 완료 | `POST /api/trades/{tradeId}/complete` | JWT, 판매자 | 없음 | 200, TradeResponse | 403: 비판매자 / 404: 거래 없음 / 409: ACCEPTED 아님 또는 상품이 RESERVED 아님 |
| 구매자 거래 목록 | `GET /api/members/{memberId}/purchases` | JWT, 본인 | 없음 | 200, TradeResponse 배열 | 403: 타인 조회 |
| 상품 거래 이력 목록 | `GET /api/products/{productId}/trades` | JWT, 판매자 | 없음 | 200, TradeResponse 배열 | 403: 비판매자 / 404: 상품 없음 |

TradeCreateRequest의 `productId`는 필수 Long이며 null·0·음수는 불가하다. 구매자는 principal로 결정한다. accept/reject/complete는 본문과 필수 query parameter 없이 호출하며 요청자가 상품 판매자인지 검사한다. 현재 MVP에서는 상품 판매자가 거래 완료를 처리한다.

TradeResponse 필드: `id`, `productId`, `buyerId`(Long), `status`(`REQUESTED`, `ACCEPTED`, `REJECTED`, `COMPLETED`).

- 거래 요청은 SELLING 상품에만 가능하고 자신의 상품에는 요청할 수 없다. 같은 상품·구매자의 활성 REQUESTED 중복을 거부한다.
- REJECTED 이후 상품이 SELLING이면 새 Trade로 재요청할 수 있다. 기존 Trade를 REQUESTED로 되돌리지 않는다.
- 수락은 REQUESTED 거래와 SELLING 상품에만 가능하다. 거래를 ACCEPTED, 상품을 RESERVED로 변경하고 같은 상품의 나머지 REQUESTED 거래를 모두 REJECTED로 변경한다.
- 거절은 REQUESTED 거래만 REJECTED로 변경하며 상품 상태는 유지한다.
- 완료는 ACCEPTED 거래와 RESERVED 상품에만 가능하며 각각 COMPLETED, SOLD로 변경한다.
- 구매 내역은 principal과 경로 memberId가 같은 경우 그 회원이 구매자로 참여한 전체 거래를 반환한다. 회원 존재 여부를 별도 조회하지 않으며 결과가 없으면 빈 배열이다.
- 상품 거래 이력은 상품 존재 및 판매자 권한을 검사한다. 구매자·무관한 회원의 조회는 거부하며, REQUESTED 이외 상태의 이력도 포함한다. 이력이 없으면 빈 배열이다.

상태 전이, 재요청 및 순차 요청 보장 범위는 [도메인 모델](domain-model.md)의 Trade 절에 정리한다.

## 6. 오류 응답 계약

### BusinessException과 ProblemDetail

Controller·Service·Entity의 비즈니스 오류는 `BusinessException`으로 표현하고 GlobalExceptionHandler가 ErrorCode의 HTTP 상태와 예외 메시지로 ProblemDetail을 생성한다. 응답 Content-Type은 `application/problem+json`이다.

| HTTP 상태 | code | 현재 사용 의미 |
|---|---|---|
| 400 | `INVALID_INPUT` | 요청 Validation·PATCH 값·본문·JSON·경로 자료형 오류 |
| 401 | `UNAUTHORIZED` | 로그인 자격 증명 실패 등 BusinessException 기반 인증 오류 |
| 403 | `FORBIDDEN` | 본인·판매자·소유자 권한 위반 |
| 404 | `NOT_FOUND` | API별 조회 대상 리소스 없음 |
| 409 | `CONFLICT` | 중복 또는 현재 상태·비즈니스 규칙과 충돌 |

기본 응답 필드는 `title`(HTTP 상태 설명), `status`(숫자), `detail`(해당 오류 메시지), `instance`(요청 경로), 확장 필드 `code`이다. 현재 직렬화에서는 기본 `type=about:blank`가 생략된다. 비즈니스 오류에는 Validation의 `errors` 배열이 없다.

예를 들어 존재하지 않는 상품의 공개 단건 조회는 다음 구조의 404를 반환한다.

```json
{
  "title": "Not Found",
  "status": 404,
  "detail": "상품을 찾을 수 없습니다.",
  "instance": "/api/products/9223372036854775807",
  "code": "NOT_FOUND"
}
```

`INTERNAL_ERROR`(500)는 ErrorCode에 정의되어 있지만, 예상 밖 예외 전체를 이 코드로 변환하는 공통 핸들러는 없다. 모든 500의 본문이 위 계약과 같다고 보장하지 않는다.

### Validation·역직렬화·PATCH 입력 오류의 차이

| 발생 경로 | HTTP / code | detail | errors |
|---|---|---|---|
| `@Valid` DTO의 Bean Validation 실패 | 400 / `INVALID_INPUT` | `요청 값이 올바르지 않습니다.` | 필드 오류 배열 포함 |
| 본문 누락·JSON 구문·DTO 역직렬화 오류 | 400 / `INVALID_INPUT` | `요청 본문이 없거나 JSON 형식 또는 자료형이 올바르지 않습니다.` | 없음 |
| 경로 등 인자 자료형 변환 실패 | 400 / `INVALID_INPUT` | `요청 경로 또는 파라미터의 자료형이 올바르지 않습니다.` | 없음 |
| PATCH의 객체·필드·값 수동 검증 실패 | 400 / `INVALID_INPUT` | `상품 수정 요청 값이 올바르지 않습니다.` | 없음 |

Bean Validation의 `errors`는 `{"field":"필드명","message":"제약 위반 메시지"}` 객체의 배열이다. 필드명, 같은 필드 안에서는 제약 코드 순으로 정렬한다. PATCH는 `@Valid` DTO 바인딩이 아니므로 같은 400이라도 이 배열을 반환하지 않는다.

### JWT 인증 실패와 로그인 실패의 차이

- JWT가 없는 보호 요청은 Security의 authentication entry point가 `sendError(401)`로 처리한다.
- 잘못되었거나 만료된 Bearer JWT는 JwtAuthenticationFilter가 `sendError(401, "Invalid or expired JWT")`로 처리한다. Controller와 GlobalExceptionHandler를 거치지 않는다.
- 위 두 경로에는 애플리케이션의 ProblemDetail·`code`·`detail` 형식을 동일하게 보장하지 않는다. 실제 오류 본문은 서블릿 오류 처리 경로에 의존한다.
- 반면 로그인 자격 증명 실패는 Service의 BusinessException이므로 401 `UNAUTHORIZED` ProblemDetail을 반환한다.
- 인증 필터가 MVC 입력 검증보다 먼저 실행된다. JWT가 없는 보호 요청이나 잘못된 JWT는 본문·경로가 잘못되어도 먼저 401로 거부될 수 있다. 여러 오류가 동시에 있을 때 표의 상태를 모두 반환하거나 고정된 순서로 검사한다는 의미는 아니다.

## 7. Swagger/OpenAPI 및 현재 범위

현재 `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml`은 공개되어 있다. 이는 업무 API 17개에 포함하지 않는 문서 경로이다.

OpenAPI에는 전역 `bearerAuth`(HTTP bearer, JWT)를 정의하고 공개 업무 API 4개는 빈 `@SecurityRequirements`로 전역 인증 요구를 해제한다. Swagger UI의 Authorize에는 로그인 응답의 accessToken 값을 입력하여 보호 API를 호출한다. 문서의 인증 표시는 실제 본인·판매자 권한 검사와 별개다.

현재 정상 응답 200 정책, 배열 목록, 순차 요청 기준의 도메인 규칙을 유지한다. 페이징·검색·동시성 제어를 구현 완료로 보지 않으며, 동시 요청의 완전한 정합성 보장은 [로드맵](roadmap.md)의 Phase 3에서 다룬다.
