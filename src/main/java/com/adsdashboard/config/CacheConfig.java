package com.adsdashboard.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 캐시는 데이터 성격에 따라 두 갈래로 나뉜다.
 *
 * <ul>
 *   <li><b>광고 매체 API</b> — Meta·Google·Naver. 실시간성이 중요하므로 짧게 5분.
 *       기본 {@link CacheManager} 라 {@code @Cacheable} 에 별도 지정이 없으면 여기로 간다.</li>
 *   <li><b>시트 데이터</b> — 신규콜·오프라인·CAC. 사람이 손으로 채우는 데이터라
 *       자주 폴링할 이유가 없다. TTL 24h 로 길게 두고
 *       {@link com.adsdashboard.common.SheetSyncScheduler} 가 하루 2회 갱신한다.
 *       (TTL 은 스케줄 누락에 대비한 백스톱일 뿐 1차 갱신 수단이 아니다.)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

  // --- 광고 매체 API 캐시 (5분) ---
  static final String META_INSIGHTS_CACHE = "metaInsights";
  static final String META_CAMPAIGNS_CACHE = "metaCampaigns";
  static final String GOOGLE_INSIGHTS_CACHE = "googleInsights";
  static final String GOOGLE_CAMPAIGNS_CACHE = "googleCampaigns";
  static final String NAVER_INSIGHTS_CACHE = "naverInsights";
  static final String NAVER_CAMPAIGNS_CACHE = "naverCampaigns";

  // --- 시트 데이터 캐시 (24h, 스케줄러가 하루 2회 갱신) ---
  public static final String LEADS_CACHE = "leads";
  public static final String OFFLINE_CACHE = "offline";
  public static final String CAC_CACHE = "cac";

  /** 광고 매체 API 용 — 기본 CacheManager. {@code @Cacheable} 의 기본 대상이다. */
  @Bean
  @Primary
  public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager(
        META_INSIGHTS_CACHE,
        META_CAMPAIGNS_CACHE,
        GOOGLE_INSIGHTS_CACHE,
        GOOGLE_CAMPAIGNS_CACHE,
        NAVER_INSIGHTS_CACHE,
        NAVER_CAMPAIGNS_CACHE);
    manager.setCaffeine(Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(200));
    return manager;
  }

  /** 시트 데이터 전용 — 시트 서비스의 {@code @Cacheable(cacheManager = "sheetCacheManager")} 가 사용. */
  @Bean
  public CacheManager sheetCacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager(
        LEADS_CACHE,
        OFFLINE_CACHE,
        CAC_CACHE);
    manager.setCaffeine(Caffeine.newBuilder()
        .expireAfterWrite(24, TimeUnit.HOURS)
        .maximumSize(50));
    return manager;
  }
}
