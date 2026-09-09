# TradeUp Database Design

## 1. 목적

Phase 1 도메인 모델을 기준으로 초기 데이터베이스 테이블 구조와 주요 제약 조건을 정의한다.

이 문서는 논리적인 데이터베이스 설계를 다루며, JPA 매핑 및 실제 DDL 구현은 이후 단계에서 결정한다.

## 2. members

### columns

- id
- email
- nickname

### constraints

- email NOT NULL
- email UNIQUE
- nickname NOT NULL
- nickname UNIQUE

## 3. products

### columns

- id
- seller_id
- title
- description
- price
- status

### constraints

- seller_id NOT NULL
- title NOT NULL
- price NOT NULL
- status NOT NULL

### relations

- seller_id -> members.id

## 4. wishlists

### columns

- id
- member_id
- product_id

### constraints

- member_id NOT NULL
- product_id NOT NULL
- UNIQUE(member_id, product_id)

### relations

- member_id -> members.id
- product_id -> products.id

## 5. trades

### columns

- id
- product_id
- buyer_id
- status

### constraints

- product_id NOT NULL
- buyer_id NOT NULL
- status NOT NULL

### relations

- product_id -> products.id
- buyer_id -> members.id

