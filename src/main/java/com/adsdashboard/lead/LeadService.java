package com.adsdashboard.lead;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class LeadService {

  private final LeadSheetFetcher sheet;
  private final LeadCsvFetcher csv;
  private final LeadProperties props;

  public LeadService(LeadSheetFetcher sheet, LeadCsvFetcher csv, LeadProperties props) {
    this.sheet = sheet;
    this.csv = csv;
    this.props = props;
  }

  @Cacheable(cacheNames = "leads", key = "'all'")
  public List<LeadEntry> all() {
    // 시트 설정돼있으면 시트 우선, 실패/empty면 CSV fallback.
    if (props.isSheetConfigured()) {
      List<LeadEntry> rows = sheet.fetchAll();
      if (!rows.isEmpty()) return rows;
    }
    return csv.fetchAll();
  }

  /** 기간 + (옵션) 본부/센터 필터 적용한 신규콜 집계. */
  public Map<String, Object> getByCategory(String datePreset, String since, String until,
                                            String hq, String center) {
    DateRange r = resolveRange(datePreset, since, until);
    List<LeadEntry> rows = all().stream()
        .filter(e -> !e.date().isBefore(r.since()) && !e.date().isAfter(r.until()))
        .filter(e -> hq == null || hq.equals("all") || hq.equals(e.hq()))
        .filter(e -> {
          if (center == null || center.equals("all")) return true;
          return center.equals(e.daycareCenter()) || center.equals(e.homecareCenter());
        })
        .toList();

    long total = rows.size();
    Map<String, Long> byService = new LinkedHashMap<>();
    byService.put("방문요양", 0L);
    byService.put("주간보호", 0L);
    byService.put("본사", 0L);
    Map<String, Long> byCenter = new LinkedHashMap<>();
    Map<String, Long> byHq = new LinkedHashMap<>();
    Map<String, Long> byDate = new LinkedHashMap<>();
    for (LeadEntry e : rows) {
      String s = e.service();
      byService.merge(s, 1L, Long::sum);
      String c = e.center();
      if (c != null) byCenter.merge(c, 1L, Long::sum);
      if (e.hq() != null) byHq.merge(e.hq(), 1L, Long::sum);
      byDate.merge(e.date().toString(), 1L, Long::sum);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("total", total);
    result.put("range", Map.of("since", r.since().toString(), "until", r.until().toString()));
    result.put("byService", byService);
    result.put("byCenter", byCenter);
    result.put("byHq", byHq);
    result.put("byDate", byDate);
    result.put("rows", rows);
    return result;
  }

  private record DateRange(LocalDate since, LocalDate until) {}

  private static DateRange resolveRange(String preset, String since, String until) {
    LocalDate today = LocalDate.now();
    if (since != null && !since.isBlank() && until != null && !until.isBlank()) {
      return new DateRange(LocalDate.parse(since), LocalDate.parse(until));
    }
    String p = (preset == null || preset.isBlank()) ? "last_7d" : preset.toLowerCase();
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
}
