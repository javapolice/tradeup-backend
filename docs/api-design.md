# TradeUp API Design

## 1. 목적

Phase 1 MVP 유스케이스를 기준으로 외부 클라이언트가 TradeUp 백엔드 기능을 호출하기 위한 초기 HTTP API를 정의한다.

현재 Phase 1에서는 인증 기능을 구현하지 않기 때문에, 사용자 식별이 필요한 API는 memberId, sellerId, buyerId 등을 명시적으로 전달한다.

향후 인증 기능이 도입되면 해당 식별 방식은 변경될 수 있다.

## 2. Member API

### 회원 가입

POST /api/members

Request
- email
- nickname

Response
- id
- email
- nickname

### 회원 조회

GET /api/members/{memberId}

Response
- id
- email
- nickname

## 3. Product API

### 상품 등록

POST /api/products

Request
- sellerId
- title
- description
- price

Response
- id
- sellerId
- title
- description
- price
- status

### 상품 단건 조회

GET /api/products/{productId}

### 판매 중 상품 목록 조회

GET /api/products

### 상품 수정

PATCH /api/products/{productId}

Request
- sellerId
- title
- description
- price

### 상품 삭제

DELETE /api/products/{productId}?sellerId={sellerId}

## 4. Wishlist API

### 상품 찜

POST /api/wishlists

Request
- memberId
- productId

### 상품 찜 취소

DELETE /api/wishlists/{wishlistId}?memberId={memberId}

### 회원 찜 목록 조회

GET /api/members/{memberId}/wishlists

## 5. Trade API

### 거래 요청

POST /api/trades

Request
- productId
- buyerId

Response
- id
- productId
- buyerId
- status

### 거래 요청 수락

POST /api/trades/{tradeId}/accept

Request
- sellerId

### 거래 요청 거절

POST /api/trades/{tradeId}/reject

Request
- sellerId

### 거래 완료

POST /api/trades/{tradeId}/complete

Request
- sellerId

### 구매자 거래 목록 조회

GET /api/members/{memberId}/purchases

### 상품 거래 요청 목록 조회

GET /api/products/{productId}/trades

