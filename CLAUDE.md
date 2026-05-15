# CLAUDE.md

이 파일은 ads_dashboard 작업 시 Claude가 따라야 할 사용자 지시사항을 기록합니다.

## 작업 지시 기록 (2026-05-15부터)

- **CLAUDE.md 업데이트 규칙**: 사용자가 명령하는 모든 지시사항을 이 파일에 누적 기록한다.
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
