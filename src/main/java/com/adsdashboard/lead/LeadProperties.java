package com.adsdashboard.lead;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 신규콜 데이터 소스 설정.
 *
 * 우선순위: sheetId 가 채워져 있으면 Google Sheets 사용, 아니면 CSV(csvPath) 사용.
 *  - sheetId / sheetRange : 구글 시트 ID + 읽을 범위 (예: "현황!A:Z").
 *                            sheetId 는 쉼표로 여러 개 지정 가능 (모두 fetch 후 합산).
 *  - serviceAccountKey    : 서비스 계정 JSON 의 내용 (env에 직접 paste)
 *  - serviceAccountKeyPath: 또는 파일 경로 (로컬 개발용)
 *  - csvPath              : 백업 CSV 경로 (sheet 미설정 시 fallback)
 */
@ConfigurationProperties(prefix = "lead")
public record LeadProperties(
    String csvPath,
    String sheetId,
    String sheetRange,
    String serviceAccountKey,
    String serviceAccountKeyPath) {

  private static final String DEFAULT_CSV = "data/leads.csv";
  private static final String DEFAULT_RANGE = "A:Z";

  public LeadProperties {
    if (csvPath == null || csvPath.isBlank()) {
      csvPath = DEFAULT_CSV;
    }
    if (sheetRange == null || sheetRange.isBlank()) {
      sheetRange = DEFAULT_RANGE;
    }
  }

  /** 쉼표로 구분된 sheetId 들의 리스트. */
  public java.util.List<String> sheetIds() {
    if (sheetId == null || sheetId.isBlank()) return java.util.List.of();
    return java.util.Arrays.stream(sheetId.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  public boolean isSheetConfigured() {
    return !sheetIds().isEmpty()
        && (
            (serviceAccountKey != null && !serviceAccountKey.isBlank())
            || (serviceAccountKeyPath != null && !serviceAccountKeyPath.isBlank())
        );
  }
}
