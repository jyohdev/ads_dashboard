package com.adsdashboard.naver;

import com.adsdashboard.naver.NaverProperties.NaverAccount;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class NaverAdsService {

  private static final String DEFAULT_DATE_PRESET = "last_7d";
  private static final String STATS_FIELDS_JSON =
      "[\"impCnt\",\"clkCnt\",\"salesAmt\",\"ccnt\",\"ctr\",\"cpc\",\"crto\"]";

  private final NaverProperties props;
  private final NaverAdsClient client;
  private final NaverClassifier classifier;
  private final Executor ioExecutor;

  public NaverAdsService(NaverProperties props, NaverAdsClient client, NaverClassifier classifier,
                         @Qualifier("ioExecutor") Executor ioExecutor) {
    this.props = props;
    this.client = client;
    this.classifier = classifier;
    this.ioExecutor = ioExecutor;
  }

  /**
   * {@code items} 의 각 원소를 {@code ioExecutor} 에서 동시에 처리하고, 결과를 입력 순서대로 모은다.
   * 7개 계정 / 캠페인 N개를 부채꼴로 호출하는 코드가 전부 이 한 곳을 거친다.
   */
  private <T, R> List<R> inParallel(List<T> items, Function<T, R> fn) {
    List<CompletableFuture<R>> futures = items.stream()
        .map(it -> CompletableFuture.supplyAsync(() -> fn.apply(it), ioExecutor))
        .toList();
    return futures.stream().map(CompletableFuture::join).toList();
  }

  @Cacheable(cacheNames = "naverCampaigns", key = "'list'")
  public Map<String, Object> listCampaigns() {
    List<NaverAccount> active = props.activeAccounts();
    List<Map<String, Object>> all = inParallel(active, client::listCampaigns).stream()
        .flatMap(List::stream)
        .collect(Collectors.toList());
    return Map.of("campaigns", all, "configuredAccounts", active.size());
  }

  @Cacheable(cacheNames = "naverCampaigns", key = "'adgroups'")
  public Map<String, Object> listAdGroups() {
    List<NaverAccount> active = props.activeAccounts();
    List<Map<String, Object>> all = inParallel(active, this::fetchAccountAdGroups).stream()
        .flatMap(List::stream)
        .collect(Collectors.toList());
    return Map.of("adGroups", all, "configuredAccounts", active.size());
  }

  @Cacheable(cacheNames = "naverInsights", key = "'campaign:' + #datePreset + ':' + #since + ':' + #until")
  public Map<String, Object> getCampaignInsights(String datePreset, String since, String until) {
    return fetchCampaignInsightsRaw(datePreset, since, until);
  }

  // Backwards-compat overload (preset only)
  public Map<String, Object> getCampaignInsights(String datePreset) {
    return getCampaignInsights(datePreset, null, null);
  }

  @Cacheable(cacheNames = "naverInsights", key = "'adgroup:' + #datePreset + ':' + #since + ':' + #until")
  public Map<String, Object> getAdGroupInsights(String datePreset, String since, String until) {
    DateSpec spec = resolveDateSpec(datePreset, since, until);
    List<NaverAccount> active = props.activeAccounts();
    List<Map<String, Object>> rows = inParallel(active, acc -> fetchAdGroupStats(acc, spec)).stream()
        .flatMap(List::stream)
        .collect(Collectors.toList());
    return Map.of(
        "stats", rows,
        "datePreset", spec.label(),
        "configuredAccounts", active.size());
  }

  public Map<String, Object> getAdGroupInsights(String datePreset) {
    return getAdGroupInsights(datePreset, null, null);
  }

  @Cacheable(cacheNames = "naverInsights", key = "'byCategory:' + #datePreset + ':' + #since + ':' + #until")
  public Map<String, Object> getInsightsByCategory(String datePreset, String since, String until) {
    // 캠페인 통계와 광고그룹명 조회는 서로 독립적인 7-계정 fan-out — 동시에 실행한다.
    CompletableFuture<Map<String, Object>> rawFuture = CompletableFuture.supplyAsync(
        () -> fetchCampaignInsightsRaw(datePreset, since, until), ioExecutor);
    Map<String, String> adgroupNamesByCampaign = fetchAdgroupNamesByCampaign();
    Map<String, Object> raw = rawFuture.join();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows =
        (List<Map<String, Object>>) raw.getOrDefault("stats", List.of());

    Map<String, Aggregate> serviceTotals = new LinkedHashMap<>();
    serviceTotals.put(NaverClassifier.SERVICE_HOMECARE, new Aggregate());
    serviceTotals.put(NaverClassifier.SERVICE_DAYCARE, new Aggregate());
    serviceTotals.put(NaverClassifier.SERVICE_OTHER, new Aggregate());

    Map<String, Map<String, Aggregate>> serviceCenter = new LinkedHashMap<>();
    serviceCenter.put(NaverClassifier.SERVICE_HOMECARE, new LinkedHashMap<>());
    serviceCenter.put(NaverClassifier.SERVICE_DAYCARE, new LinkedHashMap<>());

    Aggregate hqTotal = new Aggregate();
    Map<String, Aggregate> hqByService = new LinkedHashMap<>();
    hqByService.put(NaverClassifier.SERVICE_HOMECARE, new Aggregate());
    hqByService.put(NaverClassifier.SERVICE_DAYCARE, new Aggregate());
    hqByService.put(NaverClassifier.SERVICE_OTHER, new Aggregate());

    List<Map<String, Object>> classifiedRows = new ArrayList<>(rows.size());
    List<Map<String, Object>> hqRows = new ArrayList<>();

    for (Map<String, Object> row : rows) {
      String name = String.valueOf(row.getOrDefault("name", ""));
      String campId = String.valueOf(row.getOrDefault("id", row.getOrDefault("nccCampaignId", "")));
      String adgText = adgroupNamesByCampaign.getOrDefault(campId, "");
      NaverClassifier.Classification c = classifier.classify(name, adgText);
      serviceTotals.get(c.service()).add(row);
      if (NaverClassifier.CHANNEL_CENTER.equals(c.channel()) && c.center() != null) {
        Map<String, Aggregate> centerMap = serviceCenter.get(c.service());
        if (centerMap != null) {
          centerMap.computeIfAbsent(c.center(), k -> new Aggregate()).add(row);
        }
      }
      Map<String, Object> enriched = new LinkedHashMap<>(row);
      enriched.put("service", c.service());
      enriched.put("channel", c.channel());
      enriched.put("center", c.center());
      classifiedRows.add(enriched);
      if (NaverClassifier.CHANNEL_HQ.equals(c.channel())) {
        hqTotal.add(row);
        Aggregate svcBucket = hqByService.get(c.service());
        if (svcBucket != null) svcBucket.add(row);
        hqRows.add(enriched);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("datePreset", raw.get("datePreset"));
    result.put("configuredAccounts", raw.get("configuredAccounts"));
    result.put("accountErrors", raw.getOrDefault("accountErrors", List.of()));
    result.put("totals", Map.of(
        NaverClassifier.SERVICE_HOMECARE, serviceTotals.get(NaverClassifier.SERVICE_HOMECARE).toMap(),
        NaverClassifier.SERVICE_DAYCARE, serviceTotals.get(NaverClassifier.SERVICE_DAYCARE).toMap(),
        NaverClassifier.SERVICE_OTHER, serviceTotals.get(NaverClassifier.SERVICE_OTHER).toMap()));
    result.put("byCenter", Map.of(
        NaverClassifier.SERVICE_HOMECARE, sortedCenterList(serviceCenter.get(NaverClassifier.SERVICE_HOMECARE)),
        NaverClassifier.SERVICE_DAYCARE, sortedCenterList(serviceCenter.get(NaverClassifier.SERVICE_DAYCARE))));
    Map<String, Object> hq = new LinkedHashMap<>();
    hq.put("total", hqTotal.toMap());
    hq.put("byService", Map.of(
        NaverClassifier.SERVICE_HOMECARE, hqByService.get(NaverClassifier.SERVICE_HOMECARE).toMap(),
        NaverClassifier.SERVICE_DAYCARE, hqByService.get(NaverClassifier.SERVICE_DAYCARE).toMap(),
        NaverClassifier.SERVICE_OTHER, hqByService.get(NaverClassifier.SERVICE_OTHER).toMap()));
    hq.put("rows", hqRows);
    result.put("hq", hq);
    result.put("rows", classifiedRows);
    return result;
  }

  /** campaignId → space-joined adgroup names (across all 7 accounts). Per-account errors are skipped. */
  private Map<String, String> fetchAdgroupNamesByCampaign() {
    List<List<Map<String, Object>>> perAccount = inParallel(props.activeAccounts(), acc -> {
      try {
        return fetchAccountAdGroups(acc);
      } catch (RuntimeException e) {
        return List.<Map<String, Object>>of();
      }
    });
    Map<String, String> map = new HashMap<>();
    for (List<Map<String, Object>> adgroups : perAccount) {
      for (Map<String, Object> adg : adgroups) {
        Object campId = adg.get("nccCampaignId");
        Object adgName = adg.get("name");
        if (campId == null || adgName == null) continue;
        map.merge(String.valueOf(campId), String.valueOf(adgName), (a, b) -> a + " " + b);
      }
    }
    return map;
  }

  private Map<String, Object> fetchCampaignInsightsRaw(String datePreset, String since, String until) {
    DateSpec spec = resolveDateSpec(datePreset, since, until);
    List<NaverAccount> active = props.activeAccounts();
    // 7개 계정을 ioExecutor 에서 동시 호출 — 공용 ForkJoinPool 의존 시 1-vCPU 에선 직렬화된다.
    List<AccountResult> results = inParallel(active, acc -> {
      try {
        return new AccountResult(fetchCampaignStats(acc, spec), null);
      } catch (RuntimeException e) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("account", acc.name());
        err.put("customerId", acc.customerId());
        err.put("error", e.getMessage());
        return new AccountResult(List.of(), err);
      }
    });
    List<Map<String, Object>> rows = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();
    for (AccountResult r : results) {
      rows.addAll(r.rows);
      if (r.error != null) errors.add(r.error);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("stats", rows);
    result.put("datePreset", spec.label());
    result.put("configuredAccounts", active.size());
    result.put("accountErrors", errors);
    return result;
  }

  private record AccountResult(List<Map<String, Object>> rows, Map<String, Object> error) {}

  private static List<Map<String, Object>> sortedCenterList(Map<String, Aggregate> centerMap) {
    if (centerMap == null) return List.of();
    return centerMap.entrySet().stream()
        .map(e -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("center", e.getKey());
          m.putAll(e.getValue().toMap());
          return m;
        })
        .sorted(Comparator.comparingDouble(
            (Map<String, Object> m) -> ((Number) m.getOrDefault("spend", 0)).doubleValue())
            .reversed())
        .toList();
  }

  /** Mutable accumulator for impressions/clicks/spend/conversions. */
  private static final class Aggregate {
    long impressions;
    long clicks;
    double spend;
    long conversions;

    void add(Map<String, Object> row) {
      impressions += asLong(row.get("impCnt"));
      clicks += asLong(row.get("clkCnt"));
      spend += asDouble(row.get("salesAmt"));
      conversions += asLong(row.get("ccnt"));
    }

    Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("impressions", impressions);
      m.put("clicks", clicks);
      m.put("spend", spend);
      m.put("conversions", conversions);
      m.put("ctr", clicks == 0 ? 0.0 : (clicks * 100.0 / Math.max(1, impressions)));
      m.put("cpc", clicks == 0 ? 0.0 : (spend / clicks));
      m.put("cpl", conversions == 0 ? 0.0 : (spend / conversions));
      return m;
    }

    private static long asLong(Object v) {
      if (v == null) return 0L;
      if (v instanceof Number n) return n.longValue();
      try { return (long) Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }

    private static double asDouble(Object v) {
      if (v == null) return 0.0;
      if (v instanceof Number n) return n.doubleValue();
      try { return Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return 0.0; }
    }
  }

  /**
   * 한 계정의 전체 광고그룹 — 캠페인별 {@code listAdGroups} 호출을 ioExecutor 에서 동시에 수행한다.
   * (이전엔 캠페인 수만큼 직렬 호출이라 by-category 의 가장 큰 병목이었다.)
   */
  private List<Map<String, Object>> fetchAccountAdGroups(NaverAccount acc) {
    List<Map<String, Object>> campaigns = client.listCampaigns(acc);
    return inParallel(campaigns, camp -> {
      Object id = camp.get("nccCampaignId");
      if (id == null) return List.<Map<String, Object>>of();
      try {
        return client.listAdGroups(acc, id.toString());
      } catch (NaverAdsClient.NaverAdsApiException e) {
        Map<String, Object> err = new HashMap<>();
        err.put("nccCampaignId", id);
        err.put("error", e.getResponseBody());
        return List.<Map<String, Object>>of(err);
      }
    }).stream().flatMap(List::stream).collect(Collectors.toList());
  }

  private List<Map<String, Object>> fetchCampaignStats(NaverAccount acc, DateSpec spec) {
    List<Map<String, Object>> campaigns = client.listCampaigns(acc);
    List<String> ids = campaigns.stream()
        .map(c -> String.valueOf(c.get("nccCampaignId")))
        .filter(s -> !"null".equals(s))
        .toList();
    if (ids.isEmpty()) return List.of();
    Map<String, Object> resp = callStats(acc, ids, spec);
    return zipStatsWithMeta(resp, campaigns, "nccCampaignId", "name", "campaignTp");
  }

  private List<Map<String, Object>> fetchAdGroupStats(NaverAccount acc, DateSpec spec) {
    List<Map<String, Object>> adGroups = fetchAccountAdGroups(acc);
    List<String> ids = adGroups.stream()
        .map(g -> String.valueOf(g.get("nccAdgroupId")))
        .filter(s -> !"null".equals(s))
        .toList();
    if (ids.isEmpty()) return List.of();
    Map<String, Object> resp = callStats(acc, ids, spec);
    return zipStatsWithMeta(resp, adGroups, "nccAdgroupId", "name", "nccCampaignId");
  }

  private Map<String, Object> callStats(NaverAccount acc, List<String> ids, DateSpec spec) {
    if (spec.preset() != null) {
      return client.getStatsByDatePreset(acc, ids, STATS_FIELDS_JSON, spec.preset());
    }
    String timeRangeJson = "{\"since\":\"" + spec.since() + "\",\"until\":\"" + spec.until() + "\"}";
    return client.getStatsByTimeRange(acc, ids, STATS_FIELDS_JSON, timeRangeJson);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> zipStatsWithMeta(
      Map<String, Object> statsResp,
      List<Map<String, Object>> meta,
      String idKey,
      String... metaKeys) {
    Object data = statsResp.get("data");
    if (!(data instanceof List<?> list)) return List.of();
    Map<String, Map<String, Object>> metaById = new HashMap<>();
    for (Map<String, Object> m : meta) {
      Object id = m.get(idKey);
      if (id != null) metaById.put(String.valueOf(id), m);
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object row : list) {
      if (!(row instanceof Map<?, ?> rowMap)) continue;
      Map<String, Object> merged = new HashMap<>((Map<String, Object>) rowMap);
      Object id = merged.get("id");
      Map<String, Object> m = id != null ? metaById.get(String.valueOf(id)) : null;
      if (m != null) {
        for (String k : metaKeys) {
          if (m.containsKey(k)) merged.put(k, m.get(k));
        }
      }
      out.add(merged);
    }
    return out;
  }

  private record DateSpec(String preset, String since, String until, String label) {}

  // last_7d/last_14d/last_30d 는 "오늘 포함 N일" — Meta/Naver 기본 preset이 오늘 제외라
  // 명시적 time_range 로 통일. since/until 둘 다 주면 그대로 사용 (커스텀 범위).
  private static DateSpec resolveDateSpec(String input, String since, String until) {
    if (since != null && !since.isBlank() && until != null && !until.isBlank()) {
      return new DateSpec(null, since, until, since + "~" + until);
    }
    String preset = (input == null || input.isBlank()) ? DEFAULT_DATE_PRESET : input.toLowerCase();
    LocalDate today = LocalDate.now();
    return switch (preset) {
      case "today" -> new DateSpec("today", null, null, "today");
      case "yesterday" -> new DateSpec("yesterday", null, null, "yesterday");
      case "last_7d", "last_7_days" -> new DateSpec(
          null, today.minusDays(6).toString(), today.toString(), "last_7d");
      case "last_14d", "last_14_days" -> new DateSpec(
          null, today.minusDays(13).toString(), today.toString(), "last_14d");
      case "last_30d", "last_30_days" -> new DateSpec(
          null, today.minusDays(29).toString(), today.toString(), "last_30d");
      case "last_month" -> new DateSpec("lastmonth", null, null, "lastmonth");
      case "this_month" -> new DateSpec(
          null, YearMonth.from(today).atDay(1).toString(), today.toString(), "this_month");
      default -> new DateSpec(
          null, today.minusDays(6).toString(), today.toString(), "last_7d");
    };
  }

  private static DateSpec resolveDateSpec(String input) {
    return resolveDateSpec(input, null, null);
  }
}
