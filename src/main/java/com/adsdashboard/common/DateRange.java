package com.adsdashboard.common;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * {@code datePreset} 문자열(또는 {@code since}/{@code until} 커스텀 범위)을
 * {@code [since, until]} 날짜 구간으로 환산한 값 객체.
 *
 * <p>시트 기반 집계(신규콜·오프라인 광고비)가 각자 똑같은 환산 로직을 들고 있던 것을
 * 이 한 곳으로 모았다. 광고 매체(Meta·Google·Naver)는 API 마다 날짜 표현이 달라
 * 별도 환산 로직을 유지한다.
 */
public record DateRange(LocalDate since, LocalDate until) {

  private static final String DEFAULT_PRESET = "last_7d";

  /**
   * {@code since}·{@code until} 이 둘 다 주어지면 그 값을 그대로 쓰고,
   * 아니면 {@code preset} 을 오늘 기준 구간으로 환산한다.
   * {@code last_7d} 등은 "오늘 포함 N일" 로 해석한다.
   */
  public static DateRange resolve(String preset, String since, String until) {
    if (since != null && !since.isBlank() && until != null && !until.isBlank()) {
      return new DateRange(LocalDate.parse(since), LocalDate.parse(until));
    }
    LocalDate today = LocalDate.now();
    String p = (preset == null || preset.isBlank()) ? DEFAULT_PRESET : preset.toLowerCase();
    return switch (p) {
      case "today" -> new DateRange(today, today);
      case "yesterday" -> new DateRange(today.minusDays(1), today.minusDays(1));
      case "last_7d", "last_7_days" -> new DateRange(today.minusDays(6), today);
      case "last_14d", "last_14_days" -> new DateRange(today.minusDays(13), today);
      case "last_30d", "last_30_days" -> new DateRange(today.minusDays(29), today);
      case "this_month" -> new DateRange(YearMonth.from(today).atDay(1), today);
      case "last_month" -> {
        YearMonth lm = YearMonth.from(today).minusMonths(1);
        yield new DateRange(lm.atDay(1), lm.atEndOfMonth());
      }
      default -> new DateRange(today.minusDays(6), today);
    };
  }

  /** {@code date} 가 이 구간(양 끝 포함)에 들어오는지 여부. */
  public boolean contains(LocalDate date) {
    return !date.isBefore(since) && !date.isAfter(until);
  }
}
