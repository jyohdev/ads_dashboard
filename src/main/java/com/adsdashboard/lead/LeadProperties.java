package com.adsdashboard.lead;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 신규콜 데이터 소스 설정. CSV 파일 경로(절대/상대) 지정. */
@ConfigurationProperties(prefix = "lead")
public record LeadProperties(String csvPath) {

  private static final String DEFAULT_CSV = "data/leads.csv";

  public LeadProperties {
    if (csvPath == null || csvPath.isBlank()) {
      csvPath = DEFAULT_CSV;
    }
  }
}
