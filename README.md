# Spring Kotlin Hexagonal Architecture

Spring Boot, Kotlin, JPA, MySQL 기반의 헥사고날 아키텍처 프로젝트입니다.

## 기술 스택

- **언어**: Kotlin 1.9.20
- **프레임워크**: Spring Boot 3.2.0
- **빌드 도구**: Gradle 8.5
- **데이터베이스**: MySQL 8.0
- **ORM**: Spring Data JPA / Hibernate
- **API 문서화**: Swagger/OpenAPI 3.0
- **컨테이너**: Docker & Docker Compose

## 아키텍처

이 프로젝트는 헥사고날 아키텍처(Ports and Adapters) 패턴을 따릅니다:

```
src/main/kotlin/com/example/demo/
├── domain/                    # 도메인 레이어 (비즈니스 로직)
│   └── model/
│       └── User.kt
├── application/               # 애플리케이션 레이어 (유스케이스)
│   ├── port/
│   │   ├── input/            # Input Ports (유스케이스 인터페이스)
│   │   │   └── UserUseCase.kt
│   │   └── output/           # Output Ports (저장소 인터페이스)
│   │       └── UserRepository.kt
│   └── service/              # 유스케이스 구현체
│       └── UserService.kt
├── adapter/                   # 어댑터 레이어
│   ├── input/                # Inbound Adapters (컨트롤러)
│   │   └── web/
│   │       ├── UserController.kt
│   │       └── dto/
│   │           ├── CreateUserRequest.kt
│   │           └── UserResponse.kt
│   └── output/               # Outbound Adapters (저장소 구현)
│       └── persistence/
│           ├── UserEntity.kt
│           ├── UserJpaRepository.kt
│           └── UserRepositoryAdapter.kt
└── infrastructure/           # 인프라스트럭처 레이어 (설정)
    └── config/
        └── SwaggerConfig.kt
```

## 실행 방법

### Docker Compose 사용 (권장)

```bash
# 애플리케이션과 MySQL 함께 실행
docker-compose up --build

# 백그라운드 실행
docker-compose up -d --build

# 로그 확인
docker-compose logs -f app

# 종료
docker-compose down

# 볼륨까지 삭제
docker-compose down -v
```

### 로컬 실행

MySQL이 localhost:3306에서 실행 중이어야 합니다.

```bash
# Gradle로 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

## API 엔드포인트

애플리케이션이 실행되면 다음 URL에서 접근 가능합니다:

- **API Base URL**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

### User API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/users | 새 사용자 생성 |
| GET | /api/users | 모든 사용자 조회 |
| GET | /api/users/{id} | 특정 사용자 조회 |

### 예제 요청

**사용자 생성**
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "홍길동",
    "email": "hong@example.com"
  }'
```

**모든 사용자 조회**
```bash
curl http://localhost:8080/api/users
```

**특정 사용자 조회**
```bash
curl http://localhost:8080/api/users/1
```

## 데이터베이스 접근

```bash
# MySQL 컨테이너 접속
docker exec -it spring-kotlin-hexagonal-mysql-1 mysql -u demo_user -pdemo_password demo_db

# 테이블 확인
SHOW TABLES;

# 사용자 데이터 조회
SELECT * FROM users;
```

## 설정 파일

주요 설정은 `src/main/resources/application.yml`에 있습니다:

- 데이터베이스 연결 정보
- JPA/Hibernate 설정
- Swagger 설정
- 로깅 레벨

## 개발 가이드

### 새로운 기능 추가

1. **도메인 모델** 생성 (`domain/model/`)
2. **Input Port** 정의 (`application/port/input/`)
3. **Output Port** 정의 (`application/port/output/`)
4. **Service** 구현 (`application/service/`)
5. **Controller** 추가 (`adapter/input/web/`)
6. **Repository Adapter** 구현 (`adapter/output/persistence/`)

### 헥사고날 아키텍처 원칙

- **도메인 레이어**: 프레임워크에 독립적인 순수 비즈니스 로직
- **애플리케이션 레이어**: 유스케이스 정의 및 구현
- **어댑터 레이어**: 외부와의 인터페이스 (REST, DB 등)
- **의존성 방향**: 외부 → 내부 (어댑터 → 애플리케이션 → 도메인)

## 라이선스

MIT
