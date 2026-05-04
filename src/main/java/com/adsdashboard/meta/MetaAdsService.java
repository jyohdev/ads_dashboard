package com.adsdashboard.meta;

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
    return client.getAdAccountInsights(ACCOUNT_FIELDS, normalize(datePreset), null, null);
  }

  @Cacheable(cacheNames = "metaInsights", key = "'daily:' + #datePreset")
  public Map<String, Object> getDailyInsights(String datePreset) {
    return client.getAdAccountInsights(DAILY_FIELDS, normalize(datePreset), null, "1");
  }

  @Cacheable(cacheNames = "metaInsights", key = "'campaign:' + #datePreset")
  public Map<String, Object> getCampaignInsights(String datePreset) {
    return client.getAdAccountInsights(
        CAMPAIGN_INSIGHT_FIELDS, normalize(datePreset), "campaign", null);
  }

  @Cacheable(cacheNames = "metaCampaigns", key = "'list'")
  public Map<String, Object> listCampaigns() {
    return client.listCampaigns(CAMPAIGN_LIST_FIELDS);
  }

  private static String normalize(String datePreset) {
    return (datePreset == null || datePreset.isBlank()) ? DEFAULT_DATE_PRESET : datePreset;
  }
}
