package com.adsdashboard.meta;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MetaAdsService {

    private static final String DEFAULT_INSIGHT_FIELDS =
            "impressions,clicks,spend,ctr,cpc,reach,actions";
    private static final String DEFAULT_CAMPAIGN_FIELDS =
            "id,name,status,objective,daily_budget,lifetime_budget";

    private final MetaAdsClient client;

    public MetaAdsService(MetaAdsClient client) {
        this.client = client;
    }

    public Map<String, Object> getInsights(String datePreset) {
        String preset = (datePreset == null || datePreset.isBlank()) ? "last_7d" : datePreset;
        return client.getAdAccountInsights(DEFAULT_INSIGHT_FIELDS, preset);
    }

    public Map<String, Object> listCampaigns() {
        return client.listCampaigns(DEFAULT_CAMPAIGN_FIELDS);
    }
}
