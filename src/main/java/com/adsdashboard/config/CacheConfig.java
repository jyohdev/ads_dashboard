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

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager(
        META_INSIGHTS_CACHE,
        META_CAMPAIGNS_CACHE,
        GOOGLE_INSIGHTS_CACHE,
        GOOGLE_CAMPAIGNS_CACHE);
    manager.setCaffeine(Caffeine.newBuilder()
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .maximumSize(100));
    return manager;
  }
}
