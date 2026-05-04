# Ads Dashboard

광고 플랫폼(Meta, Google Ads, 네이버 검색광고) 통합 대시보드.
Spring Boot 3.5 + Java 17 + Gradle.

현재 구현: **Meta Marketing API**

## 로컬 실행

```bash
export META_ACCESS_TOKEN=your_token
export META_AD_ACCOUNT_ID=1234567890   # act_ 접두사 제외
./gradlew bootRun
```

## 엔드포인트

- `GET /api/meta/insights?datePreset=last_7d` — 광고 계정 인사이트
- `GET /api/meta/campaigns` — 캠페인 목록
- `GET /actuator/health` — 헬스체크

`datePreset` 값: `today`, `yesterday`, `last_7d`, `last_30d`, `this_month`, `last_month` 등.

## Meta 자격증명 발급

1. https://developers.facebook.com/apps/ → 앱 생성
2. 제품 추가 → **Marketing API**
3. 비즈니스 관리자(business.facebook.com) → 시스템 사용자 생성 → 광고 계정 권한 부여
4. 시스템 사용자에서 토큰 발급(권한: `ads_read`, `ads_management`)
5. 광고 계정 ID는 비즈니스 관리자 → 광고 계정에서 확인 (앞에 `act_` 빼고 숫자만)

## Render 배포

1. GitHub에 이 리포 push
2. https://dashboard.render.com → New → Blueprint → 리포 선택
3. `render.yaml` 자동 감지됨
4. 환경변수 입력: `META_ACCESS_TOKEN`, `META_AD_ACCOUNT_ID`
5. Deploy

무료 플랜은 15분 미사용 시 sleep — 첫 요청에 30초~1분 cold start 있음.
