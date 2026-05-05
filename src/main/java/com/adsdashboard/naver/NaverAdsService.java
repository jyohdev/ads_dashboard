package com.adsdashboard.naver;

import com.adsdashboard.naver.NaverProperties.NaverAccount;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class NaverAdsService {

  private static final String DEFAULT_DATE_PRESET = "last_7d";
  private static final String STATS_FIELDS_JSON =
      "[\"impCnt\",\"clkCnt\",\"salesAmt\",\"ccnt\",\"ctr\",\"cpc\",\"crto\"]";

  private final NaverProperties props;
  private final NaverAdsClient client;

  public NaverAdsService(NaverProperties props, NaverAdsClient client) {
    this.props = props;
    this.client = client;
  }

  @Cacheable(cacheNames = "naverCampaigns", key = "'list'")
  public Map<String, Object> listCampaigns() {
    List<NaverAccount> active = props.activeAccounts();
    List<Map<String, Object>> all = active.parallelStream()
        .flatMap(acc -> client.listCampaigns(acc).stream())
        .collect(Collectors.toList());
    return Map.of("campaigns", all, "configuredAccounts", active.size());
  }

  @Cacheable(cacheNames = "naverCampaigns", key = "'adgroups'")
  public Map<String, Object> listAdGroups() {
    List<NaverAccount> active = props.activeAccounts();
    List<Map<String, Object>> all = new ArrayList<>();
    List<CompletableFuture<List<Map<String, Object>>>> futures = active.stream()
        .map(acc -> CompletableFuture.supplyAsync(() -> fetchAccountAdGroups(acc)))
        .toList();
    for (CompletableFuture<List<Map<String, Object>>> f : futures) {
      all.addAll(f.join());
    }
    return Map.of("adGroups", all, "configuredAccounts", active.size());
  }

  @Cacheable(cacheNames = "naverInsights", key = "'campaign:' + #datePreset")
  public Map<String, Object> getCampaignInsights(String datePreset) {
    String[] range = toDateRange(datePreset);
    String timeRangeJson = "{\"since\":\"" + range[0] + "\",\"until\":\"" + range[1] + "\"}";
    List<NaverAccount> active = props.activeAccounts();
    List<Map<String, Object>> rows = active.parallelStream()
        .flatMap(acc -> fetchCampaignStats(acc, timeRangeJson).stream())
        .collect(Collectors.toList());
    return Map.of(
        "stats", rows,
        "since", range[0],
        "until", range[1],
        "configuredAccounts", active.size());
  }

  @Cacheable(cacheNames = "naverInsights", key = "'adgroup:' + #datePreset")
  public Map<String, Object> getAdGroupInsights(String datePreset) {
    String[] range = toDateRange(datePreset);
    String timeRangeJson = "{\"since\":\"" + range[0] + "\",\"until\":\"" + range[1] + "\"}";
    List<NaverAccount> active = props.activeAccounts();
    List<Map<String, Object>> rows = active.parallelStream()
        .flatMap(acc -> fetchAdGroupStats(acc, timeRangeJson).stream())
        .collect(Collectors.toList());
    return Map.of(
        "stats", rows,
        "since", range[0],
        "until", range[1],
        "configuredAccounts", active.size());
  }

  private List<Map<String, Object>> fetchAccountAdGroups(NaverAccount acc) {
    List<Map<String, Object>> campaigns = client.listCampaigns(acc);
    List<Map<String, Object>> all = new ArrayList<>();
    for (Map<String, Object> camp : campaigns) {
      Object id = camp.get("nccCampaignId");
      if (id == null) continue;
      try {
        all.addAll(client.listAdGroups(acc, id.toString()));
      } catch (NaverAdsClient.NaverAdsApiException e) {
        Map<String, Object> err = new HashMap<>();
        err.put("nccCampaignId", id);
        err.put("error", e.getResponseBody());
        all.add(err);
      }
    }
    return all;
  }

  private List<Map<String, Object>> fetchCampaignStats(NaverAccount acc, String timeRangeJson) {
    List<Map<String, Object>> campaigns = client.listCampaigns(acc);
    List<String> ids = campaigns.stream()
        .map(c -> String.valueOf(c.get("nccCampaignId")))
        .filter(s -> !"null".equals(s))
        .toList();
    if (ids.isEmpty()) return List.of();
    Map<String, Object> resp = client.getStats(acc, ids, STATS_FIELDS_JSON, timeRangeJson);
    return zipStatsWithMeta(resp, campaigns, "nccCampaignId", "name", "campaignTp");
  }

  private List<Map<String, Object>> fetchAdGroupStats(NaverAccount acc, String timeRangeJson) {
    List<Map<String, Object>> adGroups = fetchAccountAdGroups(acc);
    List<String> ids = adGroups.stream()
        .map(g -> String.valueOf(g.get("nccAdgroupId")))
        .filter(s -> !"null".equals(s))
        .toList();
    if (ids.isEmpty()) return List.of();
    Map<String, Object> resp = client.getStats(acc, ids, STATS_FIELDS_JSON, timeRangeJson);
    return zipStatsWithMeta(resp, adGroups, "nccAdgroupId", "name", "nccCampaignId");
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

  private static String[] toDateRange(String datePreset) {
    String preset = (datePreset == null || datePreset.isBlank())
        ? DEFAULT_DATE_PRESET : datePreset.toLowerCase();
    LocalDate today = LocalDate.now();
    return switch (preset) {
      case "today" -> new String[] {today.toString(), today.toString()};
      case "yesterday" -> {
        String d = today.minusDays(1).toString();
        yield new String[] {d, d};
      }
      case "last_7d", "last_7_days" ->
          new String[] {today.minusDays(6).toString(), today.toString()};
      case "last_14d", "last_14_days" ->
          new String[] {today.minusDays(13).toString(), today.toString()};
      case "last_30d", "last_30_days" ->
          new String[] {today.minusDays(29).toString(), today.toString()};
      default -> new String[] {today.minusDays(6).toString(), today.toString()};
    };
  }
}
