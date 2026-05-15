package com.adsdashboard.cac;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CAC 시트 설정. lead/offline 와 동일한 서비스 계정 자격증명 재사용.
 *  - sheetId: 시트 URL 또는 ID (gid는 무시 — 탭 이름으로 직접 조회)
 *  - hqTab: 본부별 신규수급자/비용/CAC 가 있는 탭 이름 (예: "본부 신규/비용/CAC_26년")
 *  - centerTab: 센터별 CAC/비용 탭 이름 (예: "센터별 비용/CAC_서비스 분류")
 */
@ConfigurationProperties(prefix = "cac")
public record CacProperties(
    String sheetId,
    String hqTab,
    String centerTab,
    String serviceAccountKey,
    String serviceAccountKeyPath) {

  public CacProperties {
    if (hqTab == null || hqTab.isBlank()) hqTab = "본부 신규/비용/CAC_26년";
    if (centerTab == null || centerTab.isBlank()) centerTab = "센터별 비용/CAC_서비스 분류";
  }

  public boolean isConfigured() {
    return sheetId != null && !sheetId.isBlank()
        && (
            (serviceAccountKey != null && !serviceAccountKey.isBlank())
            || (serviceAccountKeyPath != null && !serviceAccountKeyPath.isBlank())
        );
  }
}
