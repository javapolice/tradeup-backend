# TradeUp HTTP API

## 1. 현재 구현 기준

현재 Controller의 경로, 요청 전달 위치 및 응답을 정리한다. 요청 본문은 JSON이며, 아래 query parameter는 필수이다. `{memberId}`, `{productId}`, `{tradeId}`, `{wishlistId}`는 path variable이다.

현재 인증 기능은 없으며 memberId, sellerId, buyerId를 클라이언트가 전달한다. 변경 작업의 본인 여부 검사는 전달된 ID와 저장된 ID를 비교하는 수준이며 인증된 사용자 신원을 검증하지 않는다. 인증 및 조회 권한 정책은 Phase 1.5 검토 대상이다.

### HTTP 응답

- 아래 API는 성공 시 모두 `200 OK`를 반환한다. 생성 API에 `201 Created`를 적용하지 않았다.
- Product/Wishlist 삭제는 `200 OK`와 빈 본문을 반환하며 `204 No Content`를 적용하지 않았다.
- 목록 응답은 JSON 배열이며 페이징 응답은 없다.
- 비즈니스 규칙 위반 시 Service/Entity가 `IllegalArgumentException` 또는 `IllegalStateException`을 던진다. 이를 400/403/404/409 등으로 매핑하는 Global Exception Handler와 공통 오류 응답 형식은 미구현이다. Spring 기본 오류 처리에 의존하며 비즈니스 예외의 HTTP 상태/본문을 별도 계약으로 보장하지 않는다.
- 요청 DTO에 Bean Validation 제약과 Controller의 `@Valid` 적용은 없다. Validation 의존성 추가를 입력 검증 구현 완료로 보지 않는다.

## 2. Member API

| 기능 | Method / 경로 | JSON 요청 본문 | 성공 응답 |
|---|---|---|---|
| 회원 가입 | `POST /api/members` | `email`, `nickname` | Member 객체 |
| 회원 조회 | `GET /api/members/{memberId}` | 없음 | Member 객체 |

Member 응답 필드: `id`, `email`, `nickname`.

## 3. Product API

| 기능 | Method / 경로 | JSON 요청 본문 | 성공 응답 |
|---|---|---|---|
| 상품 등록 | `POST /api/products` | `sellerId`, `title`, `description`, `price` | Product 객체 |
| 상품 단건 조회 | `GET /api/products/{productId}` | 없음 | Product 객체 |
| 판매 중 상품 목록 | `GET /api/products` | 없음 | Product 배열 |
| 상품 수정 | `PATCH /api/products/{productId}` | `sellerId`, `title`, `description`, `price` | Product 객체 |
| 상품 삭제 | `DELETE /api/products/{productId}?sellerId={sellerId}` | 없음 | 빈 본문 |

Product 응답 필드: `id`, `sellerId`, `title`, `description`, `price`, `status`.

상품 삭제의 `sellerId`는 **query parameter**이다. 판매자 본인의 SELLING 상품이며 Trade 이력이 없어야 삭제 가능하다. 관련 Wishlist는 먼저 삭제한다. 상세 정책은 [요구사항](requirements.md)의 상품 삭제 절을 따른다.

상품 수정은 전달된 title, description, price로 세 필드를 모두 덮어쓴다. 생략한 필드의 기존 값을 유지하는 선택적 수정 로직은 없다. 목록 조회는 SELLING 상품을 반환하며 조건 검색 API는 미구현이다.

## 4. Wishlist API

| 기능 | Method / 경로 | JSON 요청 본문 | 성공 응답 |
|---|---|---|---|
| 상품 찜 | `POST /api/wishlists` | `memberId`, `productId` | Wishlist 객체 |
| 상품 찜 취소 | `DELETE /api/wishlists/{wishlistId}?memberId={memberId}` | 없음 | 빈 본문 |
| 회원 찜 목록 | `GET /api/members/{memberId}/wishlists` | 없음 | Wishlist 배열 |

Wishlist 응답 필드: `id`, `memberId`, `productId`. 찜 취소의 `memberId`는 **query parameter**이다.

## 5. Trade API

| 기능 | Method / 경로 | JSON 요청 본문 | 성공 응답 |
|---|---|---|---|
| 거래 요청 | `POST /api/trades` | `productId`, `buyerId` | Trade 객체 |
| 거래 수락 | `POST /api/trades/{tradeId}/accept?sellerId={sellerId}` | 없음 | Trade 객체 |
| 거래 거절 | `POST /api/trades/{tradeId}/reject?sellerId={sellerId}` | 없음 | Trade 객체 |
| 거래 완료 | `POST /api/trades/{tradeId}/complete?sellerId={sellerId}` | 없음 | Trade 객체 |
| 구매자 거래 목록 | `GET /api/members/{memberId}/purchases` | 없음 | Trade 배열 |
| 상품 거래 이력 목록 | `GET /api/products/{productId}/trades` | 없음 | Trade 배열 |

Trade 응답 필드: `id`, `productId`, `buyerId`, `status`.

accept/reject/complete의 `sellerId`는 모두 **query parameter**이며 JSON 본문으로 받지 않는다. 현재 MVP에서는 상품 판매자가 거래 완료를 처리한다.

구매자 거래 목록은 전달된 memberId 기준으로, 상품 거래 목록은 productId 기준으로 조회한다. 상품 거래 목록에는 REQUESTED 이외 상태의 이력도 포함된다. 현재 두 조회 API는 요청자의 신원이나 상품 판매자 여부를 검사하지 않는다.

상태 전이, 재요청 및 순차 요청 보장 범위는 [도메인 모델](domain-model.md)의 Trade 절에 정리한다.
