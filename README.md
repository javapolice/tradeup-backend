# TradeUp

대규모 트래픽과 실제 운영 이슈를 단계적으로 재현하고 해결하며, 최종적으로 AI Agent 기능까지 확장하는 중고거래 플랫폼 백엔드 프로젝트입니다.

서비스 기능을 많이 구현하는 것보다 백엔드에서 발생할 수 있는 문제를 재현하고, 원인을 분석하고, 해결하는 과정을 증명하는 것을 목표로 합니다.

## Current Phase

**Phase 0 — 프로젝트 기반 정의 및 개발환경 구성**

현재 프로젝트 목표와 MVP 범위, 핵심 도메인, 기술 선택 기준을 정의하고 로컬 개발환경을 구성하고 있습니다.

## Tech Stack

* Java 25 (LTS)
* Spring Boot 4.1.0
* Spring Data JPA
* PostgreSQL 18
* Gradle (Kotlin DSL)
* Docker Compose

추가 기술은 초기부터 도입하지 않고 실제 문제가 발생했을 때 필요성을 검토한 후 단계적으로 도입합니다.

## Documentation

* [MVP Requirements](docs/requirements.md)
* [Development Roadmap](docs/roadmap.md)
* [Technology Decisions](docs/tech-decisions.md)
* [Development Environment](docs/development-environment.md)
* [Architecture Decision Records](docs/adr)

## Local Development

### Requirements

로컬 실행을 위해 다음 환경이 필요합니다.

* Java 25
* Docker
* Docker Compose

### 1. Run PostgreSQL

프로젝트 루트에서 PostgreSQL을 실행합니다.

```docker compose up -d```

실행 상태를 확인합니다.

```docker compose ps```

### 2. Run Tests

MacOS / Linux:

```./gradlew test```

Windows PowerShell:

```.\gradlew.bat test```

### 3. Run Application

MacOS / Linux:

```./gradlew bootRun```

Windows PowerShell:

```.\gradlew.bat bootRun```

애플리케이션은 기본적으로 `8080`포트에서 실행됩니다.

### 4. Stop Local Infrastructure

PostgreSQL 컨테이너를 종료합니다.

```docker compose down```

데이터 볼륨까지 삭제해야 하는 경우에만 다음 명령을 사용합니다.

```docker compose down -v```

> `-v` 옵션을 사용하면 로컬 PostgreSQL 데이터가 삭제됩니다.

