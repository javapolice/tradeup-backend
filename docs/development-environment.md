# Development Environment



TradeUp의 기본 로컬 개발환경을 정의한다.



## Runtime



- Java: Eclipse Temurin JDK 25

- Spring Boot: 4.1.0



## Build



- Gradle

- Kotlin DSL



## Database



- PostgreSQL 18

- Docker Compose를 통해 로컬 환경에서 실행한다.



## Development Tools



- IntelliJ IDEA Ultimate

- Git

- Docker

- Docker Compose



## Environment Policy



개발자의 로컬 머신에 PostgreSQL을 직접 설치하지 않는다.



외부 인프라는 가능한 Docker Compose를 통해 실행하여

개발환경의 재현성을 유지한다.



애플리케이션 실행에 필요한 구체적인 버전과 설정은

프로젝트 설정 파일을 기준으로 관리한다.

