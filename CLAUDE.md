# CLAUDE.md

이 파일은 ads_dashboard 작업 시 Claude가 따라야 할 사용자 지시사항을 기록합니다.

## 작업 지시 기록 (2026-05-15부터)

- **CLAUDE.md 업데이트 규칙**: 사용자가 명령하는 모든 지시사항을 이 파일에 누적 기록한다.
- **커밋 + 푸시 규칙** (2026-05-16 변경 — 기존 자동 커밋 규칙 폐기):
  - `git commit` / `git push` 는 **사용자가 명령할 때만** 실행한다. 작업 단위가 끝나도 자동으로 커밋하지 않는다.
  - `.env` / `data/service-account.json` 같은 시크릿 파일은 절대 커밋하지 않는다(이미 `.gitignore` 처리됨).
  - 메인 브랜치 직접 push는 auto mode classifier가 막을 수 있음 — 막히면 사용자에게 알리고 `! git push origin main` 안내.
- **OAuth / 로그인**:
  - Google OAuth로 `@caring.co.kr` 도메인만 접근 가능. GCP 동의화면 Internal + 앱단 도메인 체크 2중.
  - 새 PC에서 작업 시: `.env` + `data/service-account.json` 두 파일은 git에 없으니 별도 안전 경로로 옮길 것.
- **신규콜 시트 (Google Sheets API)**:
  - 서비스 계정 방식. `LEAD_SHEET_ID`에 시트 URL/ID 쉼표로 여러 개 가능, URL의 `gid=N` 자동 파싱해 정확한 탭 매핑.
  - 받은 시트 4개 (전부 접근 OK, 4번도 2026-05-15 권한 부여 완료):
    1. `[마케팅팀 지표] 주간보호, 통합요양 마케팅 지표` → 탭 `센터별 비용/CAC_서비스 분류` (비용/CAC)
    2. `[마케팅팀] 주간보호센터+방문요양센터 마케팅 비용` → 탭 `비용_25년~` (센터별 월간 비용)
    3. `신규콜_전환_현황` → 탭 `주간보호_RAW` (신규콜 raw)
    4. `[지표] 마케팅팀 전체 데이터` → 탭 `대쉬보드_M_26년` (월별 전사 KPI)
  - **⏳ 대기 중 (2026-05-16 예정)**: 사용자가 "각 시트의 어떤 컬럼을 대시보드의 어떤 지표에 매핑할지" 결정해서 알려준다고 함. 받으면 매핑 작업 진행.
  - 현 상태: 4개 시트 다 fetch는 시도하지만 컬럼 스키마 다르므로 신규콜로 합치는 현행 로직은 1·2·4번 시트에서는 의미 없는 데이터 반환. 매핑 결정 받으면 시트별 디코더 분리 필요.
- **Google Ads 연동**:
  - 백엔드 API 연동 완료 (`/api/google/*`, v20)
  - 프런트 (`v2.html`)의 `scaffold` / `미연동` 표시 제거하고 실제 데이터 표시
  - Naver/Meta와 동일하게 캠페인을 본부·센터·서비스별로 매핑 후 통합 집계에 포함
- **본부/센터 매핑 보강 (Naver 캠페인명 참고)**:
  - `pickStrongCenter`에 신형식(`##_HQ_center_service_...`) 분기 + 멀티토큰 합성(`X 통합` → `X통합`) 추가
  - `centerToHq`가 `normalizeCenterName` fallback도 시도하도록 개선
  - CENTER_ALIAS 추가: 양천점/광주통합/광주봉날점/서울강동점/서울구로점/호남센터
- **Google 전환수 표기**:
  - Google `metrics.conversions`는 전체 전환수(전화클릭 + 폼 + 기타). 정확히 전화클릭만 잡으려면 conversion_action segment 필요 — 후속 작업.
  - 캠페인 테이블과 본부 카드의 전환수에서 소수점 노출되던 부분 `fmt.int`로 반올림 처리.
- **Spring MVC 구조 최적화 (2026-05-16)**:
  - 패키지-바이-피처 구조 유지. `common` 패키지 신설 — `GlobalExceptionHandler`(@RestControllerAdvice), `AdApiException`(매체 예외 공통 부모), `DateRange`(공통 날짜 환산), `SheetSyncScheduler`.
  - 컨트롤러별로 중복되던 `@ExceptionHandler` 제거 → `GlobalExceptionHandler` 로 일원화.
- **시트 데이터 자동 동기화 (2026-05-16)**:
  - 신규콜·오프라인·CAC 시트는 `SheetSyncScheduler` 가 매일 오전 9시·오후 3시(KST) 자동 갱신.
  - 캐시 2분할: 광고 API `cacheManager`(5분) / 시트 `sheetCacheManager`(24h). 시트 캐시는 스케줄러가 갱신 주체.
  - 실행 주기는 환경변수 `SHEET_SYNC_CRON` 으로 조정 (기본 `0 0 9,15 * * *`).
- **대시보드 성능 최적화 (2026-05-16)**:
  - 병목: `/api/naver/insights/by-category` — 광고그룹명 조회가 7계정 × 캠페인별 N+1 직렬 호출.
  - `CompletableFuture.supplyAsync`/`parallelStream` 을 executor 없이 쓰면 공용 ForkJoinPool(병렬도 = CPU-1)을 써서 1-vCPU 환경(Render)에선 사실상 직렬.
  - 해결: `config/AsyncConfig` 의 전용 `ioExecutor`(32스레드) 도입 → 네이버 fan-out 호출 전부 이 풀에서. 캠페인별 광고그룹 조회 병렬화 + by-category 의 통계/광고그룹명 동시 실행.
- **테스트 코드 (2026-05-16)**:
  - 매핑·기간 순수 로직 단위 테스트: `DateRangeTest`(기간), `NaverClassifierTest`(온라인 캠페인명→서비스/본사/센터), `CenterNameNormalizerTest`(센터명 정규화), `LeadEntryTest`(본사/서비스 분류), `OfflineMediaResolveTest`(오프라인 매체).
  - `AdsDashboardApplicationTests` 는 `@SpringBootTest(properties=...)` 로 더미 OAuth 주입 → `.env` 없이 `./gradlew test` 통과.
  - Meta·Google 본부/센터 매핑은 프런트(v2.html) JS 라 JUnit 범위 밖 — 추후 JS 테스트 러너 필요.
