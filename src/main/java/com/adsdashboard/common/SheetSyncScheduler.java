package com.adsdashboard.common;

import com.adsdashboard.cac.CacService;
import com.adsdashboard.lead.LeadService;
import com.adsdashboard.offline.OfflineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시트 기반 데이터(신규콜·오프라인 광고비·CAC)를 하루 두 번(오전·오후) 자동으로 새로고친다.
 *
 * <p>광고 매체 API 는 5분 캐시로 사실상 실시간이지만, 구글 시트는 사람이 손으로 채우는
 * 데이터라 자주 폴링할 이유가 없다. 그래서 시트 캐시({@code sheetCacheManager}, TTL 24h)는
 * 이 스케줄러가 갱신 주체가 된다 — 캐시를 비우고 곧바로 다시 채워(re-warm) 두므로
 * 사용자의 첫 요청도 대기 없이 응답한다.
 *
 * <p>실행 시각은 {@code application.yml} 의 {@code sheet-sync.cron} 으로 조정한다
 * (기본 09:00·15:00 KST). 한 시트 동기화가 실패해도 나머지는 계속 진행한다.
 */
@Component
public class SheetSyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(SheetSyncScheduler.class);

  private final LeadService leadService;
  private final OfflineService offlineService;
  private final CacService cacService;
  private final CacheManager sheetCacheManager;

  public SheetSyncScheduler(
      LeadService leadService,
      OfflineService offlineService,
      CacService cacService,
      @Qualifier("sheetCacheManager") CacheManager sheetCacheManager) {
    this.leadService = leadService;
    this.offlineService = offlineService;
    this.cacService = cacService;
    this.sheetCacheManager = sheetCacheManager;
  }

  @Scheduled(cron = "${sheet-sync.cron:0 0 9,15 * * *}", zone = "Asia/Seoul")
  public void refreshSheetData() {
    log.info("Sheet sync started");
    refresh("leads", leadService::all);
    refresh("offline", offlineService::all);
    refresh("cac", cacService::getAll);
    log.info("Sheet sync finished");
  }

  /** 캐시를 비우고 즉시 다시 채운다. 실패는 로깅만 하고 다음 시트로 넘어간다. */
  private void refresh(String cacheName, Runnable reload) {
    try {
      Cache cache = sheetCacheManager.getCache(cacheName);
      if (cache != null) {
        cache.clear();
      }
      reload.run();
      log.info("Sheet sync: '{}' refreshed", cacheName);
    } catch (Exception e) {
      log.error("Sheet sync: '{}' failed", cacheName, e);
    }
  }
}
