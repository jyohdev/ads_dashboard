package com.adsdashboard.cac;

import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CacService {

  private final CacSheetFetcher fetcher;

  public CacService(CacSheetFetcher fetcher) { this.fetcher = fetcher; }

  @Cacheable(cacheNames = "cac", key = "'all'", cacheManager = "sheetCacheManager")
  public Map<String, Object> getAll() {
    return fetcher.fetch().toApiPayload();
  }
}
