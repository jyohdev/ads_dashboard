package com.adsdashboard.offline;

import com.adsdashboard.common.DateRange;
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

  @Cacheable(cacheNames = "offline", key = "'all'", cacheManager = "sheetCacheManager")
  public List<OfflineEntry> all() {
    return fetcher.fetchAll();
  }

  public Map<String, Object> getByCategory(String datePreset, String since, String until,
                                            String hq, String center) {
    DateRange r = DateRange.resolve(datePreset, since, until);
    List<OfflineEntry> rows = all().stream()
        .filter(e -> r.contains(e.date()))
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
}
