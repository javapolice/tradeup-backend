# Step 2-2: original product list baseline

Historical measurement, before adding seller nicknames to the product list.
This is an actual prior result, not a measurement of the Step 2-3 implementation.

- Measured: 2026-10-30 14:49 KST, main / HEAD f8cf77c, with uncommitted Step 2-1/2-2 changes.
- Command: `.\gradlew.bat test --tests com.tradeup.backend.performance.ProductQueryPerformanceIntegrationTest`
- Result: 3 tests passed, 0 failures, 0 errors, 0 skipped.
- Source: the prior run's `build/test-results/test/TEST-com.tradeup.backend.performance.ProductQueryPerformanceIntegrationTest.xml`.
- Environment: Testcontainers 2.0.5, PostgreSQL 18.6, Hibernate 7.4.13.Final.
- Temporary target: `jdbc:postgresql://localhost:61985/tradeup_performance?loggerLevel=OFF`.
- Each product had a distinct seller. Fixture setup committed and cleared its persistence context before measurement.
- MockMvc executed the real Controller -> Service -> Repository -> DTO path without an outer test transaction.
- Hibernate statistics were cleared immediately before the request. Fixture writes, cleanup and Flyway were outside measurement.
- StatementInspector SQL and Hibernate SQL log messages matched in each case.

| Products | SELECT | PreparedStatement | SQL log entries | Initialized seller proxies before/after DTO |
|---:|---:|---:|---:|---:|
| 1 | 1 | 1 | 1 | 0 / 0 |
| 10 | 1 | 1 | 1 | 0 / 0 |
| 100 | 1 | 1 | 1 | 0 / 0 |

Observed SQL in all three cases:

```sql
select p1_0.id,p1_0.description,p1_0.price,p1_0.seller_id,p1_0.status,p1_0.title
from products p1_0 where p1_0.status=?
```

HTTP 200, exact response length and each product's sellerId were verified.
There were no additional member SELECTs. The ID-only response did not initialize seller proxies.
This records Hibernate SQL for synchronous requests, not database-server-wide statements or a latency benchmark.

The original test body and assertions remain in ProductQueryPerformanceIntegrationTest.
That class is explicitly disabled because its former HTTP contract is no longer implemented:
getSellingProducts now returns ProductListResponse including sellerNickname.
It must not be presented as an active baseline test for the current endpoint.
ProductNicknameNPlusOneIntegrationTest separately verifies the new contract and measures its SQL.
