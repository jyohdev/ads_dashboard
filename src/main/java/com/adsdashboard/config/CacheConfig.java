package com.adsdashboard.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  static final String META_INSIGHTS_CACHE = "metaInsights";
  static final String META_CAMPAIGNS_CACHE = "metaCampaigns";
  static final String GOOGLE_INSIGHTS_CACHE = "googleInsights";
  static final String GOOGLE_CAMPAIGNS_CACHE = "googleCampaigns";
  static final String NAVER_INSIGHTS_CACHE = "naverInsights";
  static final String NAVER_CAMPAIGNS_CACHE = "naverCampaigns";
  static final String LEADS_CACHE = "leads";

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager(
        META_INSIGHTS_CACHE,
        META_CAMPAIGNS_CACHE,
        GOOGLE_INSIGHTS_CACHE,
        GOOGLE_CAMPAIGNS_CACHE,
        NAVER_INSIGHTS_CACHE,
        NAVER_CAMPAIGNS_CACHE,
        LEADS_CACHE);
    manager.setCaffeine(Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(200));
    return manager;
  }
}
