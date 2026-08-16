\# ADR-0001: Initial Technology Stack



\## Status



Accepted



\## Context



TradeUp은 Spring 기반 백엔드 개발 역량과

데이터베이스 성능, 동시성, 확장성, 운영 문제 해결 과정을

단계적으로 증명하기 위한 프로젝트다.



또한 최종적으로 기존 백엔드 기능을

AI Agent와 Tool Calling 방식으로 확장하는 것을 목표로 한다.



초기 단계에서는 애플리케이션 구조를 단순하게 유지하면서

이후 발생하는 문제를 기반으로 기술을 단계적으로 도입한다.



\## Decision



초기 기술 스택은 다음과 같이 구성한다.



\- Java 25 (LTS)

\- Spring Boot 4.1.0

\- Spring Data JPA

\- PostgreSQL 18

\- Gradle (Kotlin DSL)

\- Docker Compose 기반 로컬 인프라

\- Single Application



\## Reason



\### Java 25



신규 프로젝트이므로 최신 LTS 버전을 사용한다.



기존 Java 경험을 활용하여 새로운 언어 학습보다

백엔드 설계와 문제 해결에 집중한다.



\### Spring Boot 4.1



Java 25와 호환되는 현재 세대의 Spring Boot를 사용한다.



신규 포트폴리오 프로젝트에서

과거 버전과의 호환성보다 최신 Spring 환경 경험을 우선한다.



\### PostgreSQL 18



관계형 데이터베이스를 기반으로

쿼리 성능, 인덱스, 트랜잭션, 락과 같은

데이터베이스 문제를 깊게 실험하기 위해 선택한다.



\### Gradle Kotlin DSL



타입 안정성과 IDE 지원을 활용하기 위해 Kotlin DSL을 사용한다.



애플리케이션 코드는 Java로 작성하며

Kotlin은 빌드 스크립트에만 사용한다.



\## Alternatives



\- Kotlin

\- Java 21

\- MySQL

\- Gradle Groovy DSL



\## Trade-offs



최신 기술 버전을 사용함으로써

일부 라이브러리나 자료가 이전 버전을 기준으로 작성되어 있을 수 있다.



필요한 경우 공식 문서를 기준으로 호환성을 확인한다.



또한 PostgreSQL 사용 경험을 새롭게 쌓아야 하는 학습 비용이 발생한다.

