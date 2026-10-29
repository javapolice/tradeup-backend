# TradeUp Domain Model

## 1. 목적

Phase 1 MVP의 상태 전이·비즈니스 규칙을 보존하고 Phase 1.5의 회원 인증·접근 권한·부분 수정 의미를 반영한다. 현재 구현·HTTP 계약은 [요구사항](requirements.md)과 [API 명세](api-design.md)를 함께 참고한다.

이 문서는 논리적인 도메인 모델을 정의하며, JPA 매핑 및 데이터베이스 구현 세부사항은 포함하지 않는다.

## 2. Member

### 책임

서비스의 회원을 나타낸다.

회원은 상품의 판매자 또는 구매자가 될 수 있다.

### 주요 속성

- id
- email
- nickname
- passwordHash: 비밀번호 원문 대신 보관하는 BCrypt 해시

### 비즈니스 규칙

- email은 중복될 수 없다.
- nickname은 중복될 수 없다.
- 신규 회원의 passwordHash는 null일 수 없다. 가입 Service가 PasswordEncoder로 해시를 생성하여 Member에 전달한다.
- 비밀번호 원문을 Member 속성에 저장하지 않으며 비밀번호·해시를 회원·로그인 응답에 노출하지 않는다.
- 해시가 null인 기존 회원의 로그인은 거부한다. 기존 데이터의 DB nullable 정책은 [DB 설계](database-design.md)에 별도로 정리한다.
- 인증된 회원은 자신의 정보·찜 목록·구매 내역만 조회할 수 있다. 회원이 소유자·판매자인지에 대한 접근 검사는 Controller·Service에서 수행한다.

Member는 회원 식별·해시 보관을 담당한다. 비밀번호 비교는 Service, JWT 발급·검증과 principal 설정은 별도의 인증 구성요소가 담당하며 JWT를 Member의 영속 속성으로 보관하지 않는다.

## 3. Product

### 책임

회원이 판매하는 중고 상품을 나타낸다.

상품의 기본 정보와 현재 판매 상태를 관리한다.

### 주요 속성

- id
- seller
- title
- description
- price
- status

### ProductStatus

- SELLING
- RESERVED
- SOLD

### 비즈니스 규칙

- 상품 생성 시 상태는 SELLING이다.
- 상품은 반드시 하나의 판매자를 가진다.
- 판매자 본인만 상품을 수정하거나 삭제할 수 있다.
- SELLING 상태에서만 수정하거나 삭제할 수 있다.
- Trade 상태와 관계없이 거래 이력이 하나라도 있으면 삭제할 수 없다.
- Trade 이력이 없는 상품은 Wishlist가 있어도 삭제할 수 있으며, 관련 Wishlist를 먼저 삭제한다.
- Trade는 비즈니스 이력으로 보존하며 Product와 함께 cascade 삭제하지 않는다.
- 수정은 요청한 제목·설명·가격만 반영한다. 미전달 필드는 유지하고 설명의 명시적 null은 삭제, 빈 문자열은 그대로 저장한다. 판매자·상태는 PATCH로 변경하지 않는다.

현재 Product는 수정 가능 상태와 Present 플래그에 따른 변경을 담당한다. HTTP 입력의 길이·자료형·가격 검증은 DTO 변환 경로에서 수행한다. Entity 자체가 모든 값 제약을 검증하는 구조는 아니며, 직접 생성·호출 경로까지 모든 불변식이 보호된다고 보장하지 않는다.

## 4. Wishlist

### 책임

회원이 관심 있는 상품을 저장한 관계를 나타낸다.

### 주요 속성

- id
- member
- product

### 비즈니스 규칙

- 회원은 자신의 상품을 찜할 수 없다.
- 동일 회원이 동일 상품을 중복으로 찜할 수 없다.
- 찜을 등록한 회원만 해당 찜을 취소할 수 있다.
- 본인 찜 목록만 조회할 수 있다. 현재 찜 등록에는 상품 판매 상태 제한이 없다.

## 5. Trade

### 책임

상품에 대한 구매 요청과 거래 진행 상태를 관리한다.

### 주요 속성

- id
- product
- buyer
- status

### TradeStatus

- REQUESTED
- ACCEPTED
- REJECTED
- COMPLETED

### 비즈니스 규칙

- SELLING 상태의 상품에만 거래를 요청할 수 있다.
- 판매자는 자신의 상품에 거래를 요청할 수 없다.
- 거래 생성 시 상태는 REQUESTED이다.
- 같은 상품에 여러 구매자가 각각 거래 요청을 생성할 수 있다.
- 같은 구매자의 같은 상품에 대한 활성 REQUESTED 요청은 중복 생성할 수 없다.
- REJECTED 이후에도 Product가 SELLING이면 새 Trade를 생성하여 재요청할 수 있다.
- 판매자만 거래 요청을 수락하거나 거절할 수 있다.
- REQUESTED 상태의 거래만 수락하거나 거절할 수 있다.
- SELLING 상태의 상품에 대한 거래 요청만 수락할 수 있다.
- 거래 요청을 수락하면 거래 상태는 ACCEPTED가 되고 상품 상태는 RESERVED가 된다.
- 수락 시 같은 상품의 나머지 REQUESTED Trade는 모두 REJECTED가 된다.
- 정상적인 순차 요청 흐름에서 하나의 상품에 하나의 거래만 수락된다. 동시 요청에 대한 완전한 보장은 Phase 3에서 다룬다.
- 거래 요청을 거절하면 Trade만 REJECTED로 바뀌며 Product 상태는 유지된다.
- ACCEPTED 상태의 거래만 완료할 수 있다.
- 완료 시 Product는 RESERVED 상태여야 한다.
- 현재 MVP에서는 상품 판매자만 거래 완료를 처리할 수 있다.
- 거래를 완료하면 거래 상태는 COMPLETED가 되고 상품 상태는 SOLD가 된다.
- 구매 내역은 구매자 본인만, 상품별 전체 거래 이력은 해당 상품 판매자만 조회할 수 있다.

판매자·구매자의 식별에는 JWT principal을 사용한다. Trade의 상태 전이와 Product의 상태 전이는 Service 트랜잭션에서 함께 처리하며, 실패 시 함께 롤백한다. 동시성 제어는 아직 적용하지 않았다.

## 6. 도메인 관계

Member 1 --- N Product

- 한 회원은 여러 상품을 판매할 수 있다.
- 하나의 상품은 한 명의 판매자를 가진다.

Member 1 --- N Wishlist

Product 1 --- N Wishlist

- Wishlist는 member와 product 사이의 관심 관계를 나타낸다.

Member 1 --- N Trade

Product 1 --- N Trade

- 회원은 구매자로 여러 거래에 참여할 수 있다.
- 하나의 상품에는 여러 거래 요청이 발생할 수 있다.

## 7. 상태 변화

### Product

SELLING

-> RESERVED

-> SOLD

거래 요청이 수락되면 :

SELLING -> RESERVED

거래가 완료되면 :

RESERVED -> SOLD

### Trade

REQUESTED

-> ACCEPTED

-> COMPLETED

또는

REQUESTED

-> REJECTED

REJECTED인 기존 Trade를 REQUESTED로 되돌리는 전이는 없다. 재요청은 SELLING 상품에 새 Trade를 생성하는 방식이다. Product를 RESERVED 또는 SOLD에서 SELLING으로 되돌리는 기능도 현재는 없다.

