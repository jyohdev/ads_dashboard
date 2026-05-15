package com.adsdashboard.offline;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class OfflineService {

  private final OfflineSheetFetcher fetcher;

  public OfflineService(OfflineSheetFetcher fetcher) {
    this.fetcher = fetcher;
  }

  @Cacheable(cacheNames = "offline", key = "'all'")
  public List<OfflineEntry> all() {
    return fetcher.fetchAll();
  }

  public Map<String, Object> getByCategory(String datePreset, String since, String until,
                                            String hq, String center) {
    DateRange r = resolveRange(datePreset, since, until);
    List<OfflineEntry> rows = all().stream()
        .filter(e -> !e.date().isBefore(r.since()) && !e.date().isAfter(r.until()))
        .filter(e -> hq == null || hq.equals("all") || hq.equals(e.hq()))
        .filter(e -> center == null || center.equals("all") || center.equals(e.center()))
        .toList();

    long totalAmount = rows.stream().mapToLong(OfflineEntry::amount).sum();
    long totalCount = rows.size();

    Map<String, Long> byMedia = new LinkedHashMap<>();
    Map<String, Long> byMediaCount = new LinkedHashMap<>();
    Map<String, Long> byHq = new LinkedHashMap<>();
    Map<String, Long> byCenter = new LinkedHashMap<>();
    Map<String, Long> byService = new LinkedHashMap<>();
    Map<String, Long> byDate = new LinkedHashMap<>();
    for (OfflineEntry e : rows) {
      String media = e.mediaType() == null ? "기타" : e.mediaType();
      byMedia.merge(media, e.amount(), Long::sum);
      byMediaCount.merge(media, 1L, Long::sum);
      if (e.hq() != null) byHq.merge(e.hq(), e.amount(), Long::sum);
      if (e.center() != null) byCenter.merge(e.center(), e.amount(), Long::sum);
      if (e.service() != null) byService.merge(e.service(), e.amount(), Long::sum);
      byDate.merge(e.date().toString(), e.amount(), Long::sum);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("totalAmount", totalAmount);
    result.put("totalCount", totalCount);
    result.put("range", Map.of("since", r.since().toString(), "until", r.until().toString()));
    result.put("byMedia", byMedia);
    result.put("byMediaCount", byMediaCount);
    result.put("byHq", byHq);
    result.put("byCenter", byCenter);
    result.put("byService", byService);
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
