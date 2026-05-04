package com.adsdashboard.meta;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MetaAdsService {

  private static final String ACCOUNT_FIELDS = "impressions,clicks,spend,ctr,cpc,reach,actions";
  private static final String DAILY_FIELDS = "date_start,impressions,clicks,spend,ctr,cpc";
  private static final String CAMPAIGN_INSIGHT_FIELDS =
      "campaign_id,campaign_name,impressions,clicks,spend,ctr,cpc,reach";
  private static final String CAMPAIGN_LIST_FIELDS =
      "id,name,status,objective,daily_budget,lifetime_budget";
  private static final String DEFAULT_DATE_PRESET = "last_7d";

  private final MetaAdsClient client;

  public MetaAdsService(MetaAdsClient client) {
    this.client = client;
  }

  public Map<String, Object> getAccountInsights(String datePreset) {
    return client.getAdAccountInsights(ACCOUNT_FIELDS, normalize(datePreset), null, null);
  }

  public Map<String, Object> getDailyInsights(String datePreset) {
    return client.getAdAccountInsights(DAILY_FIELDS, normalize(datePreset), null, "1");
  }

  public Map<String, Object> getCampaignInsights(String datePreset) {
    return client.getAdAccountInsights(
        CAMPAIGN_INSIGHT_FIELDS, normalize(datePreset), "campaign", null);
  }

  public Map<String, Object> listCampaigns() {
    return client.listCampaigns(CAMPAIGN_LIST_FIELDS);
  }

  private static String normalize(String datePreset) {
    return (datePreset == null || datePreset.isBlank()) ? DEFAULT_DATE_PRESET : datePreset;
  }
}
