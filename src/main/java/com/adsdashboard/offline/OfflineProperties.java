package com.adsdashboard.offline;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 오프라인 광고비 시트 설정. lead 와 동일한 서비스 계정 자격증명 재사용.
 *  - sheetId: 시트 URL 또는 ID. URL이면 gid 자동 파싱.
 *  - sheetRange: default "A:Z"
 *  - headerRowOffset: 시트 첫 행이 안내 메모면 1 (헤더는 2번째 행).
 *  - serviceAccountKey / serviceAccountKeyPath: lead와 같은 값 재사용
 */
@ConfigurationProperties(prefix = "offline")
public record OfflineProperties(
    String sheetId,
    String sheetRange,
    Integer headerRowOffset,
    String serviceAccountKey,
    String serviceAccountKeyPath) {

  public OfflineProperties {
    if (sheetRange == null || sheetRange.isBlank()) sheetRange = "A:Z";
    if (headerRowOffset == null) headerRowOffset = 0;
  }

  public boolean isConfigured() {
    return sheetId != null && !sheetId.isBlank()
        && (
            (serviceAccountKey != null && !serviceAccountKey.isBlank())
            || (serviceAccountKeyPath != null && !serviceAccountKeyPath.isBlank())
        );
  }
}
