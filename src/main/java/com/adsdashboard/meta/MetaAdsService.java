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

  @Cacheable(cacheNames = "metaInsights", key = "'account:' + #datePreset")
  public Map<String, Object> getAccountInsights(String datePreset) {
    return fetch(ACCOUNT_FIELDS, datePreset, null, null);
  }

  @Cacheable(cacheNames = "metaInsights", key = "'daily:' + #datePreset")
  public Map<String, Object> getDailyInsights(String datePreset) {
    return fetch(DAILY_FIELDS, datePreset, null, "1");
  }

  @Cacheable(cacheNames = "metaInsights", key = "'campaign:' + #datePreset")
  public Map<String, Object> getCampaignInsights(String datePreset) {
    return fetch(CAMPAIGN_INSIGHT_FIELDS, datePreset, "campaign", null);
  }

  @Cacheable(cacheNames = "metaCampaigns", key = "'list'")
  public Map<String, Object> listCampaigns() {
    return client.listCampaigns(CAMPAIGN_LIST_FIELDS);
  }

  private Map<String, Object> fetch(String fields, String datePreset, String level, String timeIncrement) {
    DateSpec spec = resolveDateSpec(datePreset);
    if (spec.preset() != null) {
      return client.getAdAccountInsights(fields, spec.preset(), level, timeIncrement);
    }
    return client.getAdAccountInsightsByTimeRange(
        fields, spec.since(), spec.until(), level, timeIncrement);
  }

  private record DateSpec(String preset, String since, String until) {}

  // last_7d/last_14d/last_30d 는 "오늘 포함 N일" 의미로 명시적 time_range 사용 (Meta 기본은 오늘 제외).
  private static DateSpec resolveDateSpec(String input) {
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
