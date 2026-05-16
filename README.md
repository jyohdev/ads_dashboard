# Ads Dashboard

흩어져 있는 광고·마케팅 데이터를 한 화면으로 잇는 통합 대시보드.

- **온라인 광고** — Meta · Google Ads · 네이버 검색광고 3개 매체 통합
- **오프라인 광고비 · CAC · 신규콜** — Google Sheets 연동
- **본부 / 센터 / 서비스 분류** — 캠페인명 파싱으로 자동 매핑
- **사내 SSO** — Google OAuth, `@caring.co.kr` 도메인만 접근

---

## 목차

1. [기술 스택](#기술-스택)
2. [아키텍처](#아키텍처)
3. [패키지 구조와 계층 책임](#패키지-구조와-계층-책임)
4. [데이터 동기화 전략](#데이터-동기화-전략)
5. [예외 처리](#예외-처리)
6. [본부 / 센터 / 서비스 매핑](#본부--센터--서비스-매핑)
7. [광고 매체별 연동 방식](#광고-매체별-연동-방식)
8. [로그인](#로그인)
9. [로컬 실행](#로컬-실행)
10. [API 엔드포인트](#api-엔드포인트)
11. [디렉터리 구조](#디렉터리-구조)
12. [Render 배포](#render-배포)

---

## 기술 스택

| Layer | Tech |
|---|---|
| Runtime | Java 17 · Spring Boot 3.5 · Gradle |
| Web | Spring MVC · Spring Security (OAuth2 Client) |
| Cache | Caffeine — 2-tier (광고 API 5분 / 시트 24시간) |
| Scheduling | Spring `@Scheduled` — 시트 데이터 하루 2회 동기화 |
| API 클라이언트 | Spring `RestClient` (Meta · Google Ads · Naver) · Google Sheets API SDK |
| Frontend | Static HTML · Tailwind (CDN) · ApexCharts (CDN) |
| Infra | Docker (멀티스테이지) · Render Blueprint |

---

## 아키텍처

### 요청 흐름

```
Browser
  ├─ /login.html ──[Google OAuth + 도메인 체크]── 인증
  └─ /v2.html  (단일 SPA)
        │
        └─ 병렬 fetch (progressive rendering)
              ├─ /api/meta/*      Meta Graph API
              ├─ /api/google/*    Google Ads API (GAQL)
              ├─ /api/naver/*     Naver SearchAd API (7개 계정 병렬)
              ├─ /api/leads/*     Google Sheets — 신규콜
              ├─ /api/offline/*   Google Sheets — 오프라인 광고비
              └─ /api/cac/*       Google Sheets — CAC
```

### 계층 흐름

요청은 매체와 무관하게 항상 같은 3계층을 통과한다.

```
HTTP 요청
   │
   ▼
[ Controller ]   요청 파라미터 파싱 · 응답 반환만 담당. 비즈니스 로직 없음.
   │
   ▼
[ Service ]      집계·분류·기간 계산 등 도메인 로직. @Cacheable 로 호출 결과 캐싱.
   │
   ▼
[ Client / Fetcher ]  외부 시스템(광고 API · Google Sheets)과의 통신·인증·시그니처.
   │
   ▼
외부 API / Google Sheets
```

> **왜 이렇게 나눴나** — 컨트롤러는 "HTTP를 안다", 서비스는 "도메인을 안다", 클라이언트는
> "외부 시스템을 안다". 한 계층이 두 가지를 알기 시작하면 테스트도 변경도 어려워진다.
> 예컨대 Naver의 HMAC 시그니처 로직은 `NaverAdsClient` 안에만 있고, 서비스는 그게
> HMAC인지조차 모른다.

---

## 패키지 구조와 계층 책임

### 패키지-바이-피처 (package-by-feature)

최상위 패키지는 **계층(controller/service/...)이 아니라 도메인(meta/google/naver/...)** 으로
나눈다. `meta` 패키지를 열면 Meta 관련 코드가 전부 거기 모여 있다 — 한 기능을 고치려고
여러 폴더를 오갈 필요가 없고, 패키지만 봐도 "이 서비스가 무슨 일을 하는지" 드러난다.

```
com.adsdashboard
├── AdsDashboardApplication      엔트리포인트
├── common/                      ── 매체를 가로지르는 공통 코드
├── config/                      ── 횡단 관심사 설정 (보안·캐시·스케줄링)
├── meta/        google/   naver/    ── 광고 매체 (온라인)
└── lead/        offline/  cac/       ── 시트 기반 데이터
```

### 한 피처 패키지 안의 역할

| 접미사 | 스테레오타입 | 책임 |
|---|---|---|
| `*Controller` | `@RestController` | HTTP 매핑, 파라미터 바인딩. 로직 없음 |
| `*Service` | `@Service` | 도메인 로직 — 집계·분류·기간 계산. `@Cacheable` |
| `*Client` | `@Component` | 광고 API 통신 — 인증·시그니처·재시도 |
| `*Fetcher` | `@Component` | Google Sheets 읽기·파싱 |
| `*Properties` | `@ConfigurationProperties` | 타입-세이프 설정 바인딩 (`record`) |
| `*Entry` · `*Snapshot` · `Classification` | — | 도메인 모델 / DTO (`record`) |

### `common` 패키지 — 중복 제거의 집결지

매체별 패키지에 흩어져 있던 동일한 코드를 한곳으로 모은 공통 모듈.

| 클래스 | 역할 |
|---|---|
| `GlobalExceptionHandler` | `@RestControllerAdvice` — 모든 컨트롤러의 예외를 한 곳에서 처리 |
| `AdApiException` | 3개 매체 API 예외(`MetaApiException` 등)의 공통 부모 |
| `DateRange` | `datePreset` → `[since, until]` 환산 (시트 서비스 공용) |
| `SheetSyncScheduler` | 시트 데이터 하루 2회 자동 동기화 배치 |

> **리팩터링 전/후** — 이전에는 `MetaAdsController`·`GoogleAdsController`·`NaverAdsController`가
> 각각 똑같은 `@ExceptionHandler` 두 개씩(외부 API 에러 + 일반 예외)을 복붙해 두었다.
> 컨트롤러 하나당 ~25줄, 3개면 ~75줄의 중복이었다. 지금은 `GlobalExceptionHandler` 하나가
> 모든 `@RestController`(신규 컨트롤러 포함)를 자동으로 커버한다.

---

## 데이터 동기화 전략

데이터 성격이 다르면 갱신 전략도 달라야 한다. 캐시를 두 갈래로 나눈 이유다.
(`config/CacheConfig.java`)

| 구분 | 캐시 (`CacheManager`) | TTL | 갱신 방식 | 근거 |
|---|---|---|---|---|
| 광고 매체 API | `cacheManager` (`@Primary`) | **5분** | 요청 시 lazy 갱신 | 매체 데이터는 실시간성이 중요 |
| 시트 데이터 | `sheetCacheManager` | **24시간** | **스케줄러가 하루 2회 갱신** | 사람이 손으로 채우는 데이터 — 자주 폴링할 이유 없음 |

### 시트 자동 동기화 (`SheetSyncScheduler`)

신규콜·오프라인 광고비·CAC 시트는 매일 **오전 9시·오후 3시(KST)** 에 자동 갱신된다.

```
09:00 / 15:00 KST
   │
   ▼
[ SheetSyncScheduler.refreshSheetData() ]
   │  매 시트마다:
   ├─ sheetCacheManager 의 해당 캐시를 clear()
   └─ 서비스 메서드를 즉시 호출 → 캐시 재적재 (re-warm)
       └─ 사용자의 첫 요청도 대기 없이 응답
```

- 한 시트 동기화가 실패해도 나머지는 계속 진행 (`try/catch` + 로깅).
- 24시간 TTL은 **1차 갱신 수단이 아니라 스케줄 누락에 대비한 백스톱**이다.
- 실행 주기는 환경변수로 조정: `SHEET_SYNC_CRON` (기본 `0 0 9,15 * * *`).

> **왜 캐시 무효화가 아니라 re-warm 인가** — 캐시만 비우면 다음 사용자가 시트 파싱이 끝날
> 때까지 기다린다. 스케줄러가 비운 직후 직접 한 번 호출해 채워두면, 사용자는 항상
> 캐시 히트만 만난다.

---

## 예외 처리

`common/GlobalExceptionHandler` (`@RestControllerAdvice`) 가 전 컨트롤러의 예외를 일괄 처리한다.

| 예외 | HTTP | 응답 바디 |
|---|---|---|
| `AdApiException` (Meta·Google·Naver API 실패) | 외부 API 의 원본 status | `{ error, status, details }` |
| 그 외 모든 `Exception` | 500 | `{ error, exception, message, rootCause, rootMessage }` |

매체별 API 예외는 `common/AdApiException` 을 상속한다 — `errorCode()` 만 각자 구현하면
(`"meta_api_error"` 등) 핸들러는 타입 하나(`AdApiException`)로 셋 다 처리한다.

```
AdApiException (abstract, common)
├── MetaAdsClient.MetaApiException        errorCode() = "meta_api_error"
├── GoogleAdsClient.GoogleAdsApiException errorCode() = "google_ads_api_error"
└── NaverAdsClient.NaverAdsApiException   errorCode() = "naver_ads_api_error"
```

---

## 본부 / 센터 / 서비스 매핑

캠페인 데이터는 매체마다 raw 형식이지만, 대시보드에선 **본부 / 센터 / 서비스**
(방문요양·주간보호·가족요양·요양보호사·장기요양등급·본사) 차원으로 비교해야 한다.

- **온라인 광고** — 매핑은 프런트(`v2.html`)에서 캠페인명 파싱으로 처리. Naver는
  서버사이드 `NaverClassifier` 도 함께 사용한다.
- **시트 데이터** — `lead/CenterNameNormalizer` 가 시트 raw 센터명을 대시보드 공식 명으로 정규화.

### 본부 목록

`본사 · 수도권1본부 · 수도권2본부 · 수도권3본부 · 영남본부 · 충청본부 · 호남본부`

### 서비스 분류 (`classifyByName`)

캠페인명에서 다음 우선순위로 키워드 검사:

1. **강한 키워드** — `방문요양|방요`, `주간보호|주보`, `가족요양`, `요양보호사|요보사`, `장기요양등급`
2. **브랜드검색** → `본사` 서비스
3. **`#02`~`#05` prefix** → 방문요양 / 주간보호 / 가족요양 / 요양보호사 (서비스 키워드 없는 플레이스 캠페인용)
4. **본사 키워드** fallback → `본사`
5. 매치 없음 → `기타`

### 센터 추출 (`pickStrongCenter`)

캠페인명 토큰화 후 우선순위:

1. 신형식 `##_<HQ>_<center>_<service>_...` — 언더스코어 분할, 인덱스 1이 HQ면 인덱스 2를 센터로
2. `~점` 토큰 (`부천점`, `광주남구점`)
3. `~센터` 토큰
4. `~본부` / `~통합` / `~공통` 토큰
5. 멀티토큰 합성 — `통합`/`공통` 단독 출현 시 직전 토큰과 결합 (`## 창원 통합` → `창원통합`)
6. 단독 suffix 토큰(`본부`·`센터`·`통합`·`공통`·`점`·`본사`)은 센터로 채택 안 함

### 센터 → 본부 매핑 (`centerToHq`)

1. **공식 명 직접 매칭** — `HQ_CENTERS[hq]` 리스트에서 탐색 (`부천점` → `수도권2본부`)
2. **별칭 매핑** — `CENTER_ALIAS` (광고 표기가 공식 명과 다른 경우)
3. **정규화 후 재시도** — `CENTER_NORMALIZE` 로 표준화 후 1·2 재시도

---

## 광고 매체별 연동 방식

| 매체 | 인증 방식 | 비고 |
|---|---|---|
| Meta | System User 영구 토큰 + Graph API v21.0 | `actions[]` 에서 `lead`·`offsite_conversion.fb_pixel_custom` 합산 |
| Google Ads | OAuth Refresh Token + Developer Token, GAQL v20 | `cost_micros` ÷ 10⁶ = ₩, `conversions` = 전환수(전화클릭 외 포함) |
| Naver SearchAd | HMAC-SHA256 직접 시그니처 (7개 계정 병렬) | 일별 비동기 리포트는 미연동 — 계정합 데이터만 제공 |
| 시트 (신규콜·오프라인·CAC) | 서비스 계정 + Sheets API v4 | URL의 `gid=N` 자동 파싱 → 탭 매핑 |

- Naver 7개 광고주 계정은 `parallelStream` / `CompletableFuture` 로 동시 호출 (가장 큰 성능 병목이었음).
- 자격증명(토큰·키)은 `.env` 에 분리. 템플릿은 [`.env.example`](.env.example) 참고.

---

## 로그인

- 로그인 전 모든 경로는 `/login.html` 로 리다이렉트 (`/actuator/health`·정적 로그인 페이지 제외).
- Google OAuth (`/oauth2/authorization/google`) 로 인증.
- **2중 도메인 차단**:
  1. GCP OAuth 동의 화면 **Internal** 설정 — caring.co.kr Workspace 계정만 로그인 가능
  2. 앱단 OIDC `userService` 에서 이메일 `endsWith("@" + allowedDomain)` 검증
- 허용 도메인은 환경변수 `ALLOWED_EMAIL_DOMAIN` 으로 변경 가능.

---

## 로컬 실행

```bash
# 1) 환경변수 파일 준비
cp .env.example .env
# .env 를 열어 필요한 매체의 자격증명만 채워넣기
# (안 채운 매체는 빈 응답을 반환 — 그 매체 패널만 비어 보임)

# 2) 시트 연동 시 서비스 계정 JSON 키를 data/service-account.json 으로 저장
#    (GCP IAM → 서비스 계정 → 키 → 새 키 만들기 → JSON)
#    + 대상 시트(들)를 그 서비스 계정 이메일에 "뷰어"로 공유

# 3) 실행
set -a && source .env && set +a && ./gradlew bootRun
```

브라우저: <http://localhost:8080/> → 로그인 → 대시보드(`/v2.html`)

> `./gradlew test` 는 OAuth client-id 가 비어 있으면 컨텍스트 로딩에 실패한다.
> 위처럼 `.env` 를 source 한 뒤 실행할 것. (Docker 빌드는 `bootJar -x test` 로 테스트를 건너뛴다.)

---

## API 엔드포인트

모두 인증 필요(`/actuator/health` 제외). `datePreset`: `today`·`yesterday`·`last_7d`·`last_14d`·`last_30d`·`this_month`·`last_month`. 커스텀 범위는 `since=YYYY-MM-DD&until=YYYY-MM-DD`.

### Meta `/api/meta`
| Method | Path | 설명 |
|---|---|---|
| GET | `/insights` | 광고 계정 합계 |
| GET | `/insights/daily` | 일별 (`time_increment=1`) |
| GET | `/insights/campaigns` | 캠페인별 (`level=campaign`) |
| GET | `/campaigns` | 캠페인 목록 |

### Google Ads `/api/google`
| Method | Path | 설명 |
|---|---|---|
| GET | `/insights` | 계정 합계 |
| GET | `/insights/daily` | 일별 |
| GET | `/insights/campaigns` | 캠페인별 |
| GET | `/campaigns` | 캠페인 메타 (상태·유형·예산) |

### Naver `/api/naver`
| Method | Path | 설명 |
|---|---|---|
| GET | `/insights/campaigns` | 7개 계정 캠페인 합계 |
| GET | `/insights/adgroups` | 광고그룹별 |
| GET | `/insights/by-category` | 서비스/채널/센터 분류 집계 |
| GET | `/campaigns` · `/adgroups` | 캠페인 / 광고그룹 목록 |

### 시트 데이터
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/leads/by-category?datePreset=&hq=&center=` | 신규콜 — 본부/센터/서비스/일자별 집계 |
| GET | `/api/offline/by-category?datePreset=&hq=&center=` | 오프라인 광고비 — 매체/본부/센터/서비스별 집계 |
| GET | `/api/cac` | 월별 본부·센터 단위 CAC 스냅샷 |

### 헬스체크
| Method | Path | 인증 |
|---|---|---|
| GET | `/actuator/health` | 불필요 |

---

## 디렉터리 구조

```
src/main/java/com/adsdashboard/
├── AdsDashboardApplication.java
├── common/                            매체를 가로지르는 공통 코드
│   ├── GlobalExceptionHandler.java    @RestControllerAdvice — 예외 일괄 처리
│   ├── AdApiException.java            매체 API 예외 공통 부모
│   ├── DateRange.java                 datePreset → [since, until] 환산
│   └── SheetSyncScheduler.java        시트 데이터 하루 2회 동기화
├── config/
│   ├── SecurityConfig.java            OAuth + 도메인 제한
│   ├── CacheConfig.java               2-tier 캐시 (광고 5분 / 시트 24h)
│   └── SchedulingConfig.java          @EnableScheduling
├── meta/                              Meta Marketing API
│   ├── MetaProperties.java
│   ├── MetaAdsClient.java
│   ├── MetaAdsService.java
│   └── MetaAdsController.java
├── google/                            Google Ads API
│   ├── GoogleProperties.java
│   ├── GoogleAdsClient.java
│   ├── GoogleAdsService.java
│   └── GoogleAdsController.java
├── naver/                             Naver SearchAd API
│   ├── NaverProperties.java
│   ├── NaverAdsClient.java
│   ├── NaverClassifier.java           캠페인명 → 서비스/센터 분류 (서버사이드)
│   ├── NaverAdsService.java
│   └── NaverAdsController.java
├── lead/                              신규콜 (CSV 또는 Google Sheets)
│   ├── LeadProperties.java
│   ├── LeadEntry.java                 도메인 모델
│   ├── LeadFetcher.java               fetcher 인터페이스
│   ├── LeadCsvFetcher.java            로컬 CSV
│   ├── LeadSheetFetcher.java          Sheets API + 서비스 계정
│   ├── CenterNameNormalizer.java      raw 센터명 → 공식 명 정규화
│   ├── LeadService.java
│   └── LeadController.java
├── offline/                           오프라인 광고비 (Google Sheets)
│   ├── OfflineProperties.java
│   ├── OfflineEntry.java
│   ├── OfflineSheetFetcher.java
│   ├── OfflineService.java
│   └── OfflineController.java
└── cac/                               CAC — 본부/센터 월별 (Google Sheets)
    ├── CacProperties.java
    ├── CacSnapshot.java
    ├── CacSheetFetcher.java
    ├── CacService.java
    └── CacController.java

src/main/resources/
├── application.yml                    환경변수 바인딩
└── static/
    ├── index.html                     /v2.html 로 redirect
    ├── login.html                     Google 로그인 페이지
    └── v2.html                        대시보드 SPA

data/
├── leads.example.csv                  CSV 포맷 샘플 (commit OK)
├── leads.csv                          실데이터 (gitignored)
└── service-account.json               GCP 서비스 계정 키 (gitignored)

Dockerfile · render.yaml               배포
.env.example                           환경변수 템플릿
.env                                   실 자격증명 (gitignored)
```

---

## Render 배포

1. GitHub push 되어 있어야 함.
2. [Render Dashboard](https://dashboard.render.com) → **New +** → **Blueprint**.
3. 리포 선택 → `render.yaml` 자동 감지.
4. 환경변수 입력 (`.env` 와 동일한 키, 운영용 값):
   - `META_*`, `GOOGLE_ADS_*`, `NAVER_ADS_*`, `LEAD_SHEET_*`, `OFFLINE_SHEET_*`, `CAC_SHEET_*`
   - `DASHBOARD_OAUTH_CLIENT_ID` / `_SECRET` (운영 도메인 redirect URI를 GCP에 등록)
   - `GOOGLE_SERVICE_ACCOUNT_KEY` (JSON 통째로 paste)
   - `SHEET_SYNC_CRON` (선택 — 기본 `0 0 9,15 * * *`)
5. **Apply** → 첫 빌드 5~10분.

> Render 무료 플랜은 15분 미사용 시 sleep, 다음 요청 시 30초~1분 cold start.
> 이 경우 `SheetSyncScheduler` 의 스케줄도 sleep 중엔 동작하지 않으니, 시트 동기화가
> 반드시 필요하면 유료 플랜 또는 외부 cron ping 을 권장한다.

---

## 라이선스

내부용 / 비공개 프로젝트.
