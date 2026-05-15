# CLAUDE.md

이 파일은 ads_dashboard 작업 시 Claude가 따라야 할 사용자 지시사항을 기록합니다.

## 작업 지시 기록 (2026-05-15부터)

- **CLAUDE.md 업데이트 규칙**: 사용자가 명령하는 모든 지시사항을 이 파일에 누적 기록한다.
- **자동 커밋 + 푸시 규칙**:
  - 의미 있는 작업 단위가 끝날 때마다 별도 지시 없이도 `git commit` 후 `git push origin main` 실행한다.
  - 단, 변경이 사용자 검토 대기 중이거나, 빌드/테스트가 실패한 상태에서는 보류한다.
  - `.env` / `data/service-account.json` 같은 시크릿 파일은 절대 커밋하지 않는다(이미 `.gitignore` 처리됨).
  - 메인 브랜치 직접 push는 auto mode classifier가 막을 수 있음 — 막히면 사용자에게 알리고 `! git push origin main` 안내.
- **OAuth / 로그인**:
  - Google OAuth로 `@caring.co.kr` 도메인만 접근 가능. GCP 동의화면 Internal + 앱단 도메인 체크 2중.
  - 새 PC에서 작업 시: `.env` + `data/service-account.json` 두 파일은 git에 없으니 별도 안전 경로로 옮길 것.
- **신규콜 시트 (Google Sheets API)**:
  - 서비스 계정 방식. `LEAD_SHEET_ID`에 시트 URL/ID 쉼표로 여러 개 가능, URL의 `gid=N` 자동 파싱해 정확한 탭 매핑.
  - 시트 4개를 받았으나 1·2번은 비용/CAC 데이터(다른 컬럼), 3번만 신규콜 raw, 4번은 공유 미적용. 시트별 용도/매핑 미해결 — 사용자 결정 대기.
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
