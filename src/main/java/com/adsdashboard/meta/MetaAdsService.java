package com.adsdashboard.meta;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class MetaAdsService {

  private static final String ACCOUNT_FIELDS = "impressions,clicks,spend,ctr,cpc,reach,actions";
  private static final String DAILY_FIELDS =
      "date_start,impressions,clicks,spend,ctr,cpc,actions";
  private static final String CAMPAIGN_INSIGHT_FIELDS =
      "campaign_id,campaign_name,impressions,clicks,spend,ctr,cpc,reach,actions";
  private static final String CAMPAIGN_LIST_FIELDS =
      "id,name,status,objective,daily_budget,lifetime_budget";
  private static final String DEFAULT_DATE_PRESET = "last_7d";

  private final MetaAdsClient client;

  public MetaAdsService(MetaAdsClient client) {
    this.client = client;
  }

  @Cacheable(cacheNames = "metaInsights", key = "'account:' + #datePreset + ':' + #since + ':' + #until")
  public Map<String, Object> getAccountInsights(String datePreset, String since, String until) {
    return fetch(ACCOUNT_FIELDS, datePreset, since, until, null, null);
  }

  public Map<String, Object> getAccountInsights(String datePreset) {
    return getAccountInsights(datePreset, null, null);
  }

  @Cacheable(cacheNames = "metaInsights", key = "'daily:' + #datePreset + ':' + #since + ':' + #until")
  public Map<String, Object> getDailyInsights(String datePreset, String since, String until) {
    return fetch(DAILY_FIELDS, datePreset, since, until, null, "1");
  }

  public Map<String, Object> getDailyInsights(String datePreset) {
    return getDailyInsights(datePreset, null, null);
  }

  @Cacheable(cacheNames = "metaInsights", key = "'campaign:' + #datePreset + ':' + #since + ':' + #until")
  public Map<String, Object> getCampaignInsights(String datePreset, String since, String until) {
    return fetch(CAMPAIGN_INSIGHT_FIELDS, datePreset, since, until, "campaign", null);
  }

  public Map<String, Object> getCampaignInsights(String datePreset) {
    return getCampaignInsights(datePreset, null, null);
  }

  @Cacheable(cacheNames = "metaCampaigns", key = "'list'")
  public Map<String, Object> listCampaigns() {
    return client.listCampaigns(CAMPAIGN_LIST_FIELDS);
  }

  private Map<String, Object> fetch(String fields, String datePreset, String since, String until, String level, String timeIncrement) {
    DateSpec spec = resolveDateSpec(datePreset, since, until);
    if (spec.preset() != null) {
      return client.getAdAccountInsights(fields, spec.preset(), level, timeIncrement);
    }
    return client.getAdAccountInsightsByTimeRange(
        fields, spec.since(), spec.until(), level, timeIncrement);
  }

  private record DateSpec(String preset, String since, String until) {}

  // last_7d/last_14d/last_30d 는 "오늘 포함 N일" — since/until 직접 주면 그대로 time_range.
  private static DateSpec resolveDateSpec(String input, String since, String until) {
    if (since != null && !since.isBlank() && until != null && !until.isBlank()) {
      return new DateSpec(null, since, until);
    }
    String preset = (input == null || input.isBlank()) ? DEFAULT_DATE_PRESET : input;
    LocalDate today = LocalDate.now();
    return switch (preset) {
      case "today" -> new DateSpec("today", null, null);
      case "yesterday" -> new DateSpec("yesterday", null, null);
      case "last_7d", "last_7_days" -> new DateSpec(null, today.minusDays(6).toString(), today.toString());
      case "last_14d", "last_14_days" -> new DateSpec(null, today.minusDays(13).toString(), today.toString());
      case "last_30d", "last_30_days" -> new DateSpec(null, today.minusDays(29).toString(), today.toString());
      case "this_month" -> new DateSpec("this_month", null, null);
      case "last_month" -> new DateSpec("last_month", null, null);
      default -> new DateSpec(null, today.minusDays(6).toString(), today.toString());
    };
  }
}
