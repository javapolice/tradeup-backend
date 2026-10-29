# TradeUp Database Design

## 1. 목적

Phase 1의 초기 테이블 설계와 Phase 1.5의 비밀번호 해시 추가를 반영하여 현재 스키마와 제약 조건을 정의한다. 논리적 규칙은 [도메인 모델](domain-model.md), 실행 방법은 [개발환경](development-environment.md)을 참고한다.

현재 스키마는 [Flyway migration 디렉터리](../src/main/resources/db/migration)의 V1~V5 SQL로 관리한다. JPA의 `ddl-auto`는 `none`이며 Hibernate가 테이블을 생성하지 않는다. 모든 테이블의 id는 `BIGSERIAL PRIMARY KEY`이다.

| 버전 | 변경 | 단계 |
|---|---|---|
| V1 | members 생성 | Phase 1 |
| V2 | products 생성 | Phase 1 |
| V3 | wishlists 생성 | Phase 1 |
| V4 | trades 생성 | Phase 1 |
| V5 | members.password_hash 추가 | Phase 1.5 |

## 2. members

### columns

- id
- email
- nickname
- password_hash: `VARCHAR(255)`, nullable, V5에서 추가

### constraints

- email NOT NULL
- email UNIQUE
- nickname NOT NULL
- nickname UNIQUE

### 비밀번호 해시와 기존 데이터

[V5](../src/main/resources/db/migration/V5__add_member_password_hash.sql)는 `ALTER TABLE members ADD COLUMN password_hash VARCHAR(255)`만 수행하며 NOT NULL·기본값·기존 데이터 backfill을 추가하지 않는다. 따라서 기존 회원의 해시는 null일 수 있다. JPA 매핑도 이 열을 nullable로 둔다.

신규 회원가입은 MemberService에서 BCrypt 해시를 생성하고 Member 생성자가 null 해시를 거부한다. 이는 **애플리케이션 생성 규칙**이며 DB NOT NULL 제약과 다르다. DB를 직접 조작하면 null 저장이 가능하다. 기존 null 해시 회원은 로그인에서 동일한 자격 증명 실패 401로 처리하며, 비밀번호 재설정이나 기존 해시 보완 절차는 구현되어 있지 않다.

평문 비밀번호·JWT는 이 열이나 별도 인증 테이블에 저장하지 않는다. 비밀번호·해시는 API 응답 DTO에 포함하지 않는다.

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

상태는 `VARCHAR(255)`에 enum 이름으로 저장한다. DDL에는 상태값 CHECK, 가격 범위 CHECK, 판매자와 구매자가 달라야 한다는 제약이 없다. 상태 전이와 자기 상품 거래/찜 거부는 현재 Service/Entity에서 검사한다.

Phase 1.5에서 요청 DTO의 Bean Validation과 PATCH의 수동 검증을 적용했다. 제목·설명의 최대 길이와 가격 범위 등의 HTTP 입력 제약은 [API 명세](api-design.md)를 따른다. 입력 검증이 DB CHECK나 동시성 제어를 대신하지는 않는다.

상품 PATCH의 설명 null은 DB의 `description` null로 반영하고 빈 문자열은 빈 문자열로 저장한다. 요청하지 않은 필드는 기존 값을 유지하며 Service의 쓰기 트랜잭션과 JPA Dirty Checking으로 반영한다. JWT principal에 기반한 접근 권한 검사는 애플리케이션에서 수행하며 FK만으로 인증·인가를 보장하지 않는다.

N+1 재현·조회 최적화·성능 측정은 Phase 2, 동시 요청의 정합성은 Phase 3의 후속 과제다. 현재 스키마·순차 요청 테스트를 대규모 트래픽이나 동시성 검증 완료로 해석하지 않는다.

