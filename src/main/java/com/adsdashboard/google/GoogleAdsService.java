package com.adsdashboard.google;

import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class GoogleAdsService {

  private static final String DEFAULT_DATE_PRESET = "last_7d";

  private final GoogleAdsClient client;

  public GoogleAdsService(GoogleAdsClient client) {
    this.client = client;
  }

  @Cacheable(cacheNames = "googleInsights", key = "'account:' + #datePreset")
  public Map<String, Object> getAccountInsights(String datePreset) {
    String range = toGaqlRange(datePreset);
    String gaql = "SELECT customer.id, customer.descriptive_name,"
        + " metrics.impressions, metrics.clicks, metrics.cost_micros,"
        + " metrics.ctr, metrics.average_cpc, metrics.conversions"
        + " FROM customer"
        + " WHERE segments.date DURING " + range;
    return client.search(gaql);
  }

  @Cacheable(cacheNames = "googleInsights", key = "'daily:' + #datePreset")
  public Map<String, Object> getDailyInsights(String datePreset) {
    String range = toGaqlRange(datePreset);
    String gaql = "SELECT segments.date,"
        + " metrics.impressions, metrics.clicks, metrics.cost_micros,"
        + " metrics.ctr, metrics.average_cpc, metrics.conversions"
        + " FROM customer"
        + " WHERE segments.date DURING " + range
        + " ORDER BY segments.date";
    return client.search(gaql);
  }

  @Cacheable(cacheNames = "googleInsights", key = "'campaign:' + #datePreset")
  public Map<String, Object> getCampaignInsights(String datePreset) {
    String range = toGaqlRange(datePreset);
    String gaql = "SELECT campaign.id, campaign.name,"
        + " metrics.impressions, metrics.clicks, metrics.cost_micros,"
        + " metrics.ctr, metrics.average_cpc, metrics.conversions"
        + " FROM campaign"
        + " WHERE segments.date DURING " + range;
    return client.search(gaql);
  }

  @Cacheable(cacheNames = "googleCampaigns", key = "'list'")
  public Map<String, Object> listCampaigns() {
    String gaql = "SELECT campaign.id, campaign.name, campaign.status,"
        + " campaign.advertising_channel_type, campaign_budget.amount_micros"
        + " FROM campaign";
    return client.search(gaql);
  }

  private static String toGaqlRange(String datePreset) {
    String preset = (datePreset == null || datePreset.isBlank())
        ? DEFAULT_DATE_PRESET : datePreset.toLowerCase();
    return switch (preset) {
      case "today" -> "TODAY";
      case "yesterday" -> "YESTERDAY";
      case "last_7d", "last_7_days" -> "LAST_7_DAYS";
      case "last_14d", "last_14_days" -> "LAST_14_DAYS";
      case "last_30d", "last_30_days" -> "LAST_30_DAYS";
      case "this_month" -> "THIS_MONTH";
      case "last_month" -> "LAST_MONTH";
      default -> "LAST_7_DAYS";
    };
  }
}
