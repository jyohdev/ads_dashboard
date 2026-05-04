# Ads Dashboard

흩어져 있는 광고/마케팅 데이터 파이프라인을 한 화면으로 잇는 통합 대시보드.

- **온라인 광고** (Meta · Google Ads · 네이버 검색광고)
- **오프라인 광고** (전단지 · OOH · 라디오/TV · 옥외 등 — 매체비/노출 추정 수기 입력)
- **퍼널 추적**: 노출 → 클릭/접촉 → 문의 → 신규 유입 → 수급자
- **전국 센터(지점)별** 성과 분해

**스택**: Spring Boot 3.5 · Java 17 · Gradle · Tailwind(CDN) · ApexCharts(CDN) · Caffeine

---

## 구현 현황

### ✅ 구현 완료

#### 백엔드
- Spring Boot 3.5 + Java 17 프로젝트 베이스
- Meta Marketing API 연동 (System User 영구 토큰 기반)
  - 계정 인사이트 (`/api/meta/insights`)
  - 일별 인사이트 (`/api/meta/insights/daily`, `time_increment=1`)
  - 캠페인별 인사이트 (`/api/meta/insights/campaigns`, `level=campaign`)
  - 캠페인 목록 (`/api/meta/campaigns`)
- `text/javascript` 응답 파서 (Meta Graph API quirk 대응)
- 글로벌 예외 핸들러 (Meta API 에러 / 일반 에러를 JSON으로 반환)
- Caffeine 인메모리 캐시 60초 TTL — Meta API 호출 절감
- Spring Boot Actuator 헬스체크 (`/actuator/health`)

#### 프론트엔드 (정적 SPA, `/`)
- KPI 카드 7종: 총 지출 / 노출 / 클릭 / **문의(전환)** / CTR / CPC / **CPL**
- 일별 추이 차트 — 지출(막대) + 링크클릭(라인)
- 캠페인별 지출 가로 막대 차트 (상위 10개)
- 캠페인 목록 테이블 (지출순 정렬, 상태 색상 표시)
- 날짜 필터 (오늘 / 어제 / 7일 / 30일 / 이번 달 / 지난 달)
- 5분 자동 폴링 + 수동 새로고침 버튼
- 문의 집계: `actions[]` 의 `action_type=lead` 합산

#### 인프라
- 멀티스테이지 Dockerfile (Temurin 17, JVM `-Xmx256m`)
- `render.yaml` Blueprint (Render Free 플랜)
- 코드 컨벤션: Google Java Style (2-space, 100-col)

---

### 🚧 구현 예정

#### 광고 플랫폼 추가
- **Google Ads API 연동**
  - OAuth 2.0 + Developer Token 발급
  - `google-ads-java` SDK 도입
  - GAQL 쿼리로 캠페인 / 그룹 / 키워드 지표 조회
- **네이버 검색광고 API 연동**
  - API Key + Secret + Customer ID 기반 HMAC-SHA256 시그니처 직접 구현
  - 캠페인 / 광고그룹 / 키워드 지표

#### 비즈니스 지표 확장 (퍼널 추적)
- **신규 유입** 카운트 — 문의 이후 단계 (CRM/내부 시스템 데이터 연동 또는 수동 입력)
- **수급자 전환** — 신규 유입 중 실제 수급자로 전환된 수
- 퍼널 시각화: 노출 → 클릭 → 문의 → 신규 유입 → 수급자
- 단계별 전환율 (CVR) 및 단계당 비용 (CPA) 계산
- **끊어진 데이터 파이프라인 보완**: 광고 플랫폼별/온오프라인/CRM이 분리돼서 발생하는 사각지대를
  하나의 화면에서 합산·비교

#### 온·오프라인 통합
- **오프라인 광고** 데이터 입력/관리 — 전단지, OOH, 라디오, TV, 옥외, 행사 등
  - 수기 입력 폼 (매체명, 기간, 비용, 추정 노출/도달)
  - 오프라인 → 문의 매칭 (전화 추적번호 / QR / 쿠폰 코드 / 유입 채널 응답)
- 온·오프라인 통합 KPI 비교 (CPA / CPL / ROAS)

#### 전국 센터(지점)별 분해
- **서비스 종류별 분류**: 주간보호 / 통합요양 / 방문요양 / (그 외)
- **지점별 분류**: 안산점, 광주첨단점 등 — 전국 센터 단위
- **계층 구조**: 서비스 종류 → 지역 → 개별 지점
- 센터별 KPI (지출, 문의, CPL, 신규 유입, 수급자) 동시 비교
- 지도 시각화 (지역별 성과 히트맵) 검토
- 매핑 방식:
  - 1차: 캠페인명 파싱 (현재 네이밍 `본부_트래픽_{서비스}_{지점}_숫자` 활용)
  - 2차: 매핑 안 되는 캠페인은 별도 매핑 테이블에 수동 등록

#### 대시보드 UX
- 플랫폼 탭 (Meta / Google / Naver / 오프라인 / 통합)
- 센터별 필터 (전국 / 지역 / 개별 센터)
- 캠페인별 상세 페이지 (광고세트 → 광고 단위 드릴다운)
- CSV / Excel 내보내기
- 사용자 지정 날짜 범위 (datepicker)
- 로그인 / 사용자별 권한 (현재는 익명)

#### 운영
- Render 프로덕션 배포
- 모니터링 / 에러 추적 (Sentry 또는 Spring Actuator + Prometheus)
- DB 도입 검토 — 현재는 매번 Meta API 호출, 장기 적재가 필요해지면 Postgres + 적재 배치

---

## 로컬 실행

```bash
# 환경변수 설정 (한 번만)
export META_ACCESS_TOKEN='시스템_사용자_영구_토큰'
export META_AD_ACCOUNT_ID='1234567890'   # act_ 접두사 제외, 숫자만
export META_API_VERSION=v21.0            # 선택, 기본값 v21.0

# 실행
./gradlew bootRun
```

브라우저에서 [http://localhost:8080/](http://localhost:8080/) 접속.

---

## API 엔드포인트

`datePreset` 값: `today`, `yesterday`, `last_7d`, `last_30d`, `this_month`, `last_month`, `lifetime` 등.

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/meta/insights?datePreset=last_7d` | 광고 계정 합계 인사이트 |
| GET | `/api/meta/insights/daily?datePreset=last_7d` | 일별 분해 (`time_increment=1`) |
| GET | `/api/meta/insights/campaigns?datePreset=last_7d` | 캠페인별 분해 (`level=campaign`) |
| GET | `/api/meta/campaigns` | 캠페인 목록 (이름/상태/예산) |
| GET | `/actuator/health` | 헬스체크 |

---

## Meta 자격증명 발급

1. [Meta for Developers](https://developers.facebook.com/apps/) 에서 앱 생성 (앱 유형: Business)
2. **이용 사례** → 권한 추가 (`ads_read`, `ads_management`, `business_management`)
3. [비즈니스 관리자](https://business.facebook.com) → 비즈니스 설정 → **사용자 → 시스템 사용자** 생성
4. 시스템 사용자에 광고 계정 + 앱 자산 추가 (역할: 관리자)
5. 시스템 사용자 → **토큰 생성** (만료: 영구, 권한: `ads_read` + `ads_management`)
6. 광고 계정 ID는 비즈니스 설정 → 계정 → 광고 계정에서 확인 (`act_` 접두사 제외)

> ⚠️ 토큰은 영구이므로 노출 시 즉시 폐기 + 재발급. `.env` 파일은 `.gitignore` 처리됨.

---

## Render 배포

1. GitHub 리포가 push 되어 있어야 함
2. [Render Dashboard](https://dashboard.render.com) → **New +** → **Blueprint**
3. 리포 선택 — `render.yaml` 자동 감지
4. 환경변수 입력
   - `META_ACCESS_TOKEN`
   - `META_AD_ACCOUNT_ID`
   - `META_API_VERSION` (기본 `v21.0`)
5. **Apply** 클릭 — 첫 빌드 5~10분

> Render 무료 플랜은 15분 미사용 시 sleep, 다음 요청에서 30초~1분 cold start.
> 메모리 한도 512MB이므로 JVM `-Xmx256m`으로 제한해두었음.

---

## 프로젝트 구조

```
src/main/java/com/adsdashboard/
├── AdsDashboardApplication.java
├── config/
│   └── CacheConfig.java          Caffeine 캐시 설정
└── meta/
    ├── MetaProperties.java       자격증명 record (@ConfigurationProperties)
    ├── MetaAdsClient.java        Graph API HTTP 호출 (RestClient)
    ├── MetaAdsService.java       비즈니스 로직 + @Cacheable
    └── MetaAdsController.java    REST 엔드포인트 + 예외 핸들러

src/main/resources/
├── application.yml               PORT, META_* 환경변수 바인딩
└── static/
    └── index.html                대시보드 SPA (Tailwind + ApexCharts)

Dockerfile                        멀티스테이지 빌드 (Render용)
render.yaml                       Render Blueprint
```

---

## 라이선스

내부용 / 비공개 프로젝트.
