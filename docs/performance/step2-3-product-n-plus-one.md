# Step 2-3: seller nickname N+1 reproduction

Measured on 2026-10-30 at approximately 15:01 KST, main / HEAD f8cf77c plus uncommitted Phase 2 work.
The original ID-only response baseline is preserved separately in [Step 2-2](step2-2-product-baseline.md).

## Feature and transaction boundary

GET /api/products now returns ProductListResponse with id, sellerId, title, description, price, status and sellerNickname.
ProductService.getSellingProducts uses the unchanged findAllByStatus(SELLING), then maps DTOs inside its class-level read-only transaction.
ProductListResponse.from calls product.getSeller().getNickname().
ProductResponse, the repository query, entity mappings, security rules and production configuration remain unchanged.
No fetch join, EntityGraph, projection, cache or batch fetching optimization was added.

## Measurement

- Testcontainers 2.0.5, PostgreSQL 18.6, Hibernate 7.4.13.Final.
- N+1 test target: jdbc:postgresql://localhost:63450/tradeup_performance?loggerLevel=OFF.
- Each product references a different seller.
- Fixtures are flushed, cleared and committed before the request, using a separate transaction.
- No outer HTTP test transaction; OSIV remains false.
- Statistics are enabled only for the measurement test and cleared immediately before each request.
- MeasuredSqlInspector captures the synchronous request's Hibernate SQL; a bounded Logback appender independently captures org.hibernate.SQL messages.
- A temporary test interceptor observes the real repository result before DTO mapping and is removed after each request. It verifies an active read-only transaction.
- All observed SQL is SELECT, the inspector and SQL log agree, and prepareStatementCount matches their statement count.

| Products | Product SELECT | Member SELECT | Total SELECT | PreparedStatement | Seller proxies initialized before/after DTO |
|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 1 | 2 | 2 | 0 / 1 |
| 10 | 1 | 10 | 11 | 11 | 0 / 10 |
| 100 | 1 | 100 | 101 | 101 | 0 / 100 |

Actual SQL patterns:

```sql
select p1_0.id,p1_0.description,p1_0.price,p1_0.seller_id,p1_0.status,p1_0.title
from products p1_0 where p1_0.status=?

select m1_0.id,m1_0.email,m1_0.nickname,m1_0.password_hash
from members m1_0 where m1_0.id=?
```

The member SELECT repeats once per distinct seller. There is no join or batch IN query.
HTTP 200, exact product IDs/count, all previous list fields, sellerId and sellerNickname values are asserted.
No fixture writes, cleanup, Flyway or manual flush occurs in the measurement window.

## Validation

```powershell
.\gradlew.bat test --tests com.tradeup.backend.performance.ProductNicknameNPlusOneIntegrationTest --tests com.tradeup.backend.product.controller.IsolatedProductControllerIntegrationTest --tests com.tradeup.backend.product.service.ProductServiceTest --tests com.tradeup.backend.performance.PostgresIsolationSmokeTest
```

Final result: 89 passed, 0 failures, 0 errors, 0 skipped in the selected execution.

- New nickname measurements and exact create/detail/update/delete response contracts: 4 passed.
- Existing product Controller tests inherited by the isolated subclass: 72 passed.
- Existing ProductService unit tests: 12 passed.
- Existing isolated PostgreSQL smoke test: 1 passed.

The original Controller test class was not selected directly; its isolated subclass uses the fail-fast container initializer.
The full test suite and development database were not used.
The historical Step 2-2 test is explicitly disabled and was not selected in this run; its body/assertions and prior measurements are preserved.

The first attempt failed in test instrumentation: Mockito callRealMethod could not invoke an abstract Spring Data repository method.
Replacing that observation with a temporary Spring proxy interceptor resolved it without changing business code or query-count expectations.

## Limits

This demonstrates N+1 for synchronous HTTP requests with distinct sellers. Shared sellers can reduce repeat loads through the persistence context.
StatementInspector observes SQL before JDBC preparation, not server-wide execution; HTTP success, Hibernate statement statistics and SQL logs provide cross-checks.
Driver/pool-internal SQL, latency, throughput and load behavior are not measured.
