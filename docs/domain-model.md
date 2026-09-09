# TradeUp Domain Model

## 1. 목적

Phase 1 MVP 요구사항을 기준으로 핵심 도메인의 책임, 주요 속성, 관계 및 상태 변화를 정의한다.

이 문서는 논리적인 도메인 모델을 정의하며, JPA 매핑 및 데이터베이스 구현 세부사항은 포함하지 않는다.

## 2. Member

### 책임

서비스의 회원을 나타낸다.

회원은 상품의 판매자 또는 구매자가 될 수 있다.

### 주요 속성

- id
- email
- nickname

### 비즈니스 규칙

- email은 중복될 수 없다.
- nickname은 중복될 수 없다.

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
- 판매자만 거래 요청을 수락하거나 거절할 수 있다.
- REQUESTED 상태의 거래만 수락하거나 거절할 수 있다.
- SELLING 상태의 상품에 대한 거래 요청만 수락할 수 있다.
- 거래 요청을 수락하면 거래 상태는 ACCEPTED가 되고 상품 상태는 RESERVED가 된다.
- 하나의 상품에서는 하나의 거래 요청만 수락될 수 있다.
- ACCEPTED 상태의 거래만 완료할 수 있다.
- 거래를 완료하면 거래 상태는 COMPLETED가 되고 상품 상태는 SOLD가 된다.

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

