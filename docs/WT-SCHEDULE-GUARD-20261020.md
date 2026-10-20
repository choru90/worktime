# WT-SCHEDULE-GUARD-20261020 · v1 · pending_acceptance

## 지시와 범위
2026-10-20 11:26 Asia/Seoul 사용자 “승인 하고 다음으로 넘어가줘”. 직원 앱 PR3 결과를 수용하여 e6dc331 head를 app main d6ab854에 일반 merge했다. 다음 단계로 일정 수락 충돌·동시성을 점검한다. 안녕 PR20도 같은 지시로 병합됐으며 실제 PostgreSQL 검증은 별도 저장소에서 관리한다.

이번 범위는 백엔드 일정 수락/수정/상태변경의 일관성과 회귀 검증이다. 직원별 worker 잠금 후 schedule을 refresh하여 이전 상태로 중복 수락하지 않도록 한다. 생성·월별 자동 생성도 같은 잠금 순서를 사용한다. 확정 일정의 실제 구간 겹침을 DB에서 검사하고 [시작,종료) 기준으로 인접한 근무는 허용한다. 기존 ±1일 검색은 긴 일정에 포함된 짧은 근무를 놓칠 수 있어 제거한다. READ_COMMITTED로 잠금 대기 뒤 최신 커밋을 읽고, 고정근무 생성의 상위 트랜잭션도 같은 격리 수준을 사용한다. 다중 직원 잠금은 UUID 순으로 정렬한다.

## 검증 계획/진행
신규 통합6개: 겹치는 두 제안 동시 수락 한 건만 확정, 동일 제안 중복 수락, 기존 확정과 충돌 시 pending 유지, 인접 구간 허용, 긴 일정 포함 관계, 확정 일정 수정 충돌 시 기존 시각 유지.
로컬은 H2이고 GitHub Actions의 실제 MySQL8.0 격리 전용 worktime_schedule_test DB에서도 같은6개를 실행한다. 전용 테스트 URL·DB명·계정을 고정하며 운영 DB에 create-drop하지 않는다. 전체 기존 테스트와 Flyway startup 검사는 유지한다. 로컬 결과: 전체89개 중83개 성공·실패0·기존 opt-in MySQL migration6개 생략. 신규 일정 통합6개는 모두 성공. bootJar도 통과했다. 실제 MySQL8.0 전용 worktime_schedule_test의 신규 일정 통합6개도 성공했다. 근거: https://github.com/choru90/worktime/actions/runs/39360411984 (ci job118425031845), MySQL schedule concurrency acceptance 단계 success. 검증한 코드 head9f2e646471a0d697b8d95369757031685b97a055. 이 후속 보고 커밋은 Markdown만 변경하며 검증한 소스는 동일하다. 동일 run의 전체 CI, 기존 MySQL/Flyway startup, 이미지 빌드도 success. deploy job은 branch/PR 조건에 따라 skipped이며 실제 배포하지 않았다.

테스트 환경의 동적 Java agent attach 제한을 피하기 위해 subclass Mockito mock maker를 사용한다. 테스트를 생략하거나 동시성 검증을 mock으로 대체하지 않는다.

## 배포와 비용
이 브랜치 push/PR에서는 기존 조건에 따라 deploy가 실행되지 않는다. 다만 **이 백엔드 PR을 main에 병합하면 기존 개발 환경 자동배포가 실행된다.** 병합/배포 전 결과 수용이 필요하며 이번에는 PR 검증까지만 진행한다. Docker/배포 스크립트·AWS 리소스·DB migration 변경 없음. 새 유료 자원0원, 모델비용 미측정.

## 한계/복구
실기기 및 실제 사용자 E2E 미수행. 격리 DB의 자동 테스트가 근거이며 기존 운영 일정 데이터의 정합성을 일괄 수정하지 않는다. 실패 시 원인을 고친 후 같은 검사를 다시 실행한다. 코드 revert로 복구하며 DB 스키마 변경은 없다.
