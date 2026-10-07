# TradeUp Database Design

## 1. 목적

Phase 1 도메인 모델을 기준으로 초기 데이터베이스 테이블 구조와 주요 제약 조건을 정의한다.

현재 스키마는 `src/main/resources/db/migration`의 Flyway V1~V4 SQL로 관리한다. JPA의 `ddl-auto`는 `none`이며 Hibernate가 테이블을 생성하지 않는다. 모든 테이블의 id는 `BIGSERIAL PRIMARY KEY`이다.

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

### 거래 요청 중복과 재요청

- `UNIQUE(product_id, buyer_id)`는 두지 않는다. REJECTED 이후 같은 구매자가 새 Trade로 재요청할 수 있어야 하기 때문이다.
- 같은 상품·구매자의 활성 REQUESTED 중복은 Service의 존재 여부 조회로 거부한다.
- 현재 DDL에는 활성 요청 중복을 막는 별도 UNIQUE 제약이 없으므로 동시 요청까지 완전히 보장하지 않는다. 동시성 제어는 Phase 3에서 다룬다.

## 6. Product 삭제와 이력 보존

- 판매자 본인의 SELLING 상품만 삭제할 수 있다.
- Trade 상태와 관계없이 거래 이력이 하나라도 있으면 Service가 Product 삭제를 거부한다.
- Trade 이력이 없는 경우 Service가 같은 트랜잭션에서 관련 Wishlist를 먼저 삭제하고 Product를 삭제한다.
- wishlists/trades의 Product FK에는 `ON DELETE CASCADE`가 없으며, JPA 관계에도 cascade 삭제를 설정하지 않았다.
- Trade는 비즈니스 이력이므로 Product 삭제에 따라 함께 삭제하지 않는다.

## 7. 현재 제약의 범위

상태는 `VARCHAR(255)`에 enum 이름으로 저장한다. DDL에는 상태값 CHECK, 가격 범위 CHECK, 판매자와 구매자가 달라야 한다는 제약이 없다. 상태 전이와 자기 상품 거래/찜 거부는 현재 Service/Entity에서 검사한다. 요청 값 Validation과 DB 성능 최적화는 향후 검토 대상이다.

