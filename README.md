# Ads Dashboard

흩어져 있는 광고/마케팅 데이터 파이프라인을 한 화면으로 잇는 통합 대시보드.

- **온라인 광고**: Meta · Google Ads · 네이버 검색광고 (3개 매체 통합)
- **신규콜 데이터**: Google Sheets 연동
- **본부/센터/서비스 분류**: 캠페인명 파싱으로 자동 매핑
- **사내 SSO**: Google OAuth, `@caring.co.kr` 도메인만 접근

---

## 스택

| Layer | Tech |
|---|---|
| Runtime | Java 17 · Spring Boot 3.5 · Gradle |
| Web | Spring MVC · Spring Security (OAuth2 Client) |
| Cache | Caffeine (5분 TTL) |
| API 클라이언트 | Spring `RestClient` (Meta · Google Ads · Naver), Google Sheets API SDK |
| Frontend | Static HTML · Tailwind (CDN) · ApexCharts (CDN) |
| Infra | Docker (멀티스테이지) · Render Blueprint |

---

## 아키텍처 개요

```
Browser
  ├─ /login.html ──[Google OAuth + 도메인체크]── 인증
  └─ /v2.html  (단일 SPA)
        │
        └─ 병렬 fetch (progressive rendering)
              ├─ /api/meta/*       (Meta Graph API)
              ├─ /api/google/*     (Google Ads API v20, GAQL)
              ├─ /api/naver/*      (Naver SearchAd API, 7개 계정 병렬)
              └─ /api/leads/*      (Google Sheets API, 서비스 계정)
```

각 매체 호출 결과는 Caffeine 캐시(5분)로 묶여 중복 호출을 막습니다. Naver는 7개 광고주 계정을 `parallelStream`으로 동시에 가져옵니다.

---

## 본부 / 센터 / 서비스 매핑

캠페인 데이터는 매체마다 raw 형식이지만, 대시보드 차원에선 **본부 / 센터 / 서비스(방문요양·주간보호·가족요양·요양보호사·장기요양등급·본사)** 로 분류해서 비교해야 합니다.

매핑은 모두 프런트(`v2.html`)에서 캠페인명 파싱으로 처리합니다.

### 본부 목록

`본사 · 수도권1본부 · 수도권2본부 · 수도권3본부 · 영남본부 · 충청본부 · 호남본부`

### 서비스 분류 (`classifyByName`)

캠페인명에서 다음 키워드를 우선순위로 검사:

1. **강한 키워드** — `방문요양|방요`, `주간보호|주보`, `가족요양`, `요양보호사|요보사`, `장기요양등급`
2. **브랜드검색** → `본사` 서비스
3. **#02 / #03 / #04 / #05 prefix** → 각각 방문요양 / 주간보호 / 가족요양 / 요양보호사 (서비스 키워드 없는 플레이스 캠페인용)
4. **본사 키워드** fallback → `본사`
5. 매치 없음 → `기타`

### 채널 분류

- 캠페인명에 `본사` 키워드 포함 → `본사` 채널 (HQ 캠페인)
- 그 외 → `센터` 채널

### 센터 추출 (`pickStrongCenter`)

캠페인명 토큰화 후 다음 우선순위로 센터 추출:

1. **신형식 `##_<HQ>_<center>_<service>_...`** — 언더스코어 분할, 인덱스 1이 HQ_LIST에 있으면 인덱스 2를 센터로 채택
2. `~점` 으로 끝나는 토큰 (예: `부천점`, `광주남구점`)
3. `~센터` 토큰
4. `~본부` / `~통합` / `~공통` 토큰
5. **멀티토큰 합성** — `통합`/`공통`이 단독으로 나오면 직전 토큰과 결합 (예: `## 창원 통합` → `창원통합`)
6. 단독 suffix 토큰(`본부`, `센터`, `통합`, `공통`, `점`, `본사`)은 센터로 채택 안 함

### 센터 → 본부 매핑 (`centerToHq`)

1. **공식 명 직접 매칭**: `HQ_CENTERS[hq]` 리스트에서 그대로 찾기 (예: `부천점` → `수도권2본부`)
2. **별칭 매핑**: `CENTER_ALIAS` (광고 데이터 표기가 공식 명과 다른 경우 — 예: `광주통합`, `서울영등포점`, `(구)고은센터` 등)
3. **정규화 후 재시도**: `CENTER_NORMALIZE` 로 표준 명으로 바꾼 뒤 다시 1·2 시도 (예: `양천구` → `서울양천점` → `수도권3본부`)

---

## 광고 매체별 연동 방식

| 매체 | 방식 | 비고 |
|---|---|---|
| Meta | System User 영구 토큰 + Graph API v21.0 | `actions[]` 에서 `lead`, `offsite_conversion.fb_pixel_custom` 합산 |
| Google Ads | OAuth Refresh Token + Developer Token, GAQL v20 | `metrics.cost_micros` ÷ 10^6 = ₩, `metrics.conversions` = 전환수 (전화클릭 외 포함) |
| Naver SearchAd | HMAC-SHA256 직접 시그니처 (7개 계정 병렬) | 일별 비동기 리포트는 미연동 (계정합 데이터만 제공) |
| 신규콜 (Sheets) | 서비스 계정 + Sheets API v4 | URL의 `gid=N` 자동 파싱 → 탭 이름 lookup |

자격증명(토큰/키 등)은 `.env`에 분리. 템플릿은 [`.env.example`](.env.example) 참고.

---

## 로그인

- 로그인 전 모든 경로는 `/login.html` 로 리다이렉트 (`/actuator/health` 와 정적 로그인 페이지 제외)
- Google OAuth (`/oauth2/authorization/google`) 로 인증
- 2중 차단:
  1. GCP OAuth 동의 화면 **Internal** 설정 — caring.co.kr Workspace 계정만 로그인 가능
  2. 앱단 OIDC userService에서 이메일 `endsWith("@" + allowedDomain)` 검증
- 허용 도메인은 환경변수 `ALLOWED_EMAIL_DOMAIN`으로 변경 가능

---

## 로컬 실행

```bash
# 1) 환경변수 파일 준비
cp .env.example .env
# .env 를 열어서 필요한 매체의 자격증명만 채워넣기
# (안 채운 매체는 빈 응답을 반환하므로 그 매체 패널만 비어보임)

# 2) 신규콜 시트 쓸 거면 서비스 계정 JSON 키를 data/service-account.json 으로 저장
#    (GCP IAM → 서비스 계정 → 키 → 새 키 만들기 → JSON)
#    + 대상 시트(들)를 그 서비스 계정 이메일에 "뷰어"로 공유

# 3) 실행
set -a && source .env && set +a && ./gradlew bootRun
```

브라우저: <http://localhost:8080/> → 로그인 → 대시보드(`/v2.html`)

---

## API 엔드포인트

모두 인증 필요(`/actuator/health` 제외). `datePreset` 값: `today`, `yesterday`, `last_7d`, `last_14d`, `last_30d`, `this_month`, `last_month`, `custom` (custom 일 때 `since=YYYY-MM-DD&until=YYYY-MM-DD` 추가).

### Meta
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/meta/insights?datePreset=...` | 광고 계정 합계 |
| GET | `/api/meta/insights/daily?datePreset=...` | 일별 (`time_increment=1`) |
| GET | `/api/meta/insights/campaigns?datePreset=...` | 캠페인별 (`level=campaign`) |
| GET | `/api/meta/campaigns` | 캠페인 목록 |

### Google Ads
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/google/insights?datePreset=...` | 계정 합계 |
| GET | `/api/google/insights/daily?datePreset=...` | 일별 |
| GET | `/api/google/insights/campaigns?datePreset=...` | 캠페인별 |
| GET | `/api/google/campaigns` | 캠페인 메타 (상태/유형/예산) |

### Naver
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/naver/insights?datePreset=...` | 7개 계정 합계 |
| GET | `/api/naver/insights/by-category?datePreset=...` | 서비스/채널/센터 분류 후 캠페인 단위 |
| GET | `/api/naver/campaigns` | 캠페인 목록 |

### 신규콜
| Method | Path | 설명 |
|---|---|---|
| GET | `/api/leads/by-category?datePreset=...&hq=&center=` | 본부/센터/서비스/일자별 집계 |

### 헬스체크
| Method | Path | 설명 |
|---|---|---|
| GET | `/actuator/health` | 인증 불필요 |

---

## 프로젝트 구조

```
src/main/java/com/adsdashboard/
├── AdsDashboardApplication.java
├── config/
│   ├── CacheConfig.java              Caffeine 캐시 5분 TTL
│   └── SecurityConfig.java           OAuth + 도메인 제한
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
│   ├── NaverClassifier.java          캠페인명 → 서비스/센터 분류 (서버사이드)
│   ├── NaverAdsService.java
│   └── NaverAdsController.java
└── lead/                              신규콜 (CSV 또는 Google Sheets)
    ├── LeadProperties.java
    ├── LeadFetcher.java              인터페이스
    ├── LeadCsvFetcher.java           로컬 CSV
    ├── LeadSheetFetcher.java         Sheets API + 서비스 계정 (URL의 gid 자동 매핑)
    ├── LeadService.java
    └── LeadController.java

src/main/resources/
├── application.yml                    환경변수 바인딩
└── static/
    ├── index.html                     /v2.html 로 redirect
    ├── login.html                     Google 로그인 페이지
    └── v2.html                        대시보드 SPA (KPI/카드/차트/매핑)

data/
├── leads.example.csv                  CSV 포맷 샘플 (commit OK)
├── leads.csv                          실데이터 (gitignored)
└── service-account.json               GCP 서비스 계정 키 (gitignored)

Dockerfile · render.yaml               배포
.env.example                           환경변수 템플릿
.env                                   실 자격증명 (gitignored)
```

---

## 캐싱 정책

| 캐시 | TTL | 키 |
|---|---|---|
| `metaInsights` / `metaCampaigns` | 5분 | `datePreset` 별 |
| `googleInsights` / `googleCampaigns` | 5분 | `datePreset` 별 |
| `naverInsights` / `naverCampaigns` | 5분 | `datePreset` 별 |
| `leads` | 5분 | 전체 (`'all'` 키) |

운영에서 더 길게 가져갈 필요가 있으면 `CacheConfig.java` 의 `expireAfterWrite` 조정.

---

## Render 배포

1. GitHub push 되어 있어야 함
2. [Render Dashboard](https://dashboard.render.com) → **New +** → **Blueprint**
3. 리포 선택 → `render.yaml` 자동 감지
4. 환경변수 입력 (`.env` 와 동일한 키, 운영용 값)
   - `META_*`, `GOOGLE_ADS_*`, `NAVER_ADS_*`, `LEAD_SHEET_*`
   - `DASHBOARD_OAUTH_CLIENT_ID / _SECRET` (운영 도메인용 redirect URI를 GCP에 추가해둘 것)
   - `GOOGLE_SERVICE_ACCOUNT_KEY` (JSON 통째로 paste)
5. **Apply** → 첫 빌드 5~10분

> Render 무료 플랜은 15분 미사용 시 sleep, 다음 요청 시 30초~1분 cold start.

---

## 라이선스

내부용 / 비공개 프로젝트.
