# Worktime API

소규모 매장의 직원·스케줄·근무기록·시급·예상 급여를 관리하는 Spring Boot/Kotlin API다. Kotlin 1.9.20, Spring Boot 3.2.0, JDK 17 이상, Gradle wrapper, MySQL을 사용한다.

## 현재 기능

- 사장/직원 가입과 JWT 로그인, 초대 코드 기반 직원 계정 연결
- 매장·영업시간·고정 근무·직원·퇴사 관리
- 스케줄 요청·제안·승인·거절·취소, 고정 근무 월별 생성
- 직원 출퇴근 및 확정 스케줄의 수동 근무시간 입력
- 사장 근무기록 승인·반려, 기간별 시급계약과 월별 예상 급여
- 대시보드와 서비스 내 알림

대체 근무의 요청→후보 수락→관리자 최종 승인 흐름은 아직 구현되지 않았다. 급여 조회는 세금·보험·법정 수당을 포함한 확정 급여가 아니다.

## 검증

```sh
./gradlew test
```

단위 테스트와 `WorktimeFlowIntegrationTest`를 실행한다. 통합 테스트는 합성 데이터와 H2 메모리 DB로 JWT 필터·컨트롤러·서비스·JPA 경로를 검증하며 각 테스트 뒤 롤백한다. H2 의존성은 testRuntimeOnly다. MySQL Flyway 실행 결과와 실제 브라우저 검증은 별도 확인해야 한다.

## 개발 실행

MySQL과 분리된 개발용 DB를 준비하고 `src/main/resources/application.yml`의 datasource와 JWT 설정 키에 맞는 개발 전용 값을 환경변수 또는 로컬 설정으로 제공한다. 비밀값을 커밋하지 않는다.

```sh
./gradlew bootRun
```

Docker 구성은 Dockerfile과 docker-compose.yml을 참고한다. 기존 Compose의 설정과 볼륨을 확인하고 개발용으로 격리한 뒤 사용한다.

- 기본 API: `http://localhost:8080/api`
- Swagger: `http://localhost:8080/swagger-ui/index.html`
- 웹 저장소: `choru90/worktime-web`, 개발 프록시 `/api` → localhost:8080

## 핵심 API

| 경로 | 용도 |
|---|---|
| /api/auth | 가입·로그인 |
| /api/worker/schedules | 직원 본인 일정 조회·응답·근무시간 입력 |
| /api/worklogs/my | 직원 본인 출퇴근 |
| /api/work-logs | 관리자 근무기록 조회·승인·반려 |
| /api/payroll/owner/monthly | 관리자 월별 예상 급여 |

세부 HTTP method와 요청 DTO는 컨트롤러와 Swagger를 기준으로 확인한다.

## 알려진 제한

- 일부 근무기록·일정 API는 역할 검증 외에 소속/소유권 검증이 빠져 있다. 공개 서비스 전 보완이 필요하다.
- 현재 급여 쿼리는 COMPLETED와 CONFIRMED를 함께 합산하므로 승인 전 기록도 포함될 수 있다.
- 권한 거절 예외가 공통 예외 처리에서 500으로 변환될 수 있다.
- 월 경계·시간대·자정 횡단·휴게시간과 중복 근무기록 정책은 추가 검증이 필요하다.

## 구조

`domain/model`은 엔티티와 상태, `application/service`는 업무 로직, `application/port`는 인터페이스, `adapter/input/web`은 API, `adapter/output/persistence`는 JPA, `src/main/resources/db/migration`은 Flyway 마이그레이션이다. 현재 도메인 엔티티는 JPA 어노테이션을 사용한다.
