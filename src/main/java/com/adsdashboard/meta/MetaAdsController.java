package com.adsdashboard.meta;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
public class MetaAdsController {

  private static final String DEFAULT_DATE_PRESET = "last_7d";

  private final MetaAdsService service;

  public MetaAdsController(MetaAdsService service) {
    this.service = service;
  }

  @GetMapping("/insights")
  public Map<String, Object> insights(
      @RequestParam(required = false, defaultValue = DEFAULT_DATE_PRESET) String datePreset) {
    return service.getAccountInsights(datePreset);
  }

  @GetMapping("/insights/daily")
  public Map<String, Object> dailyInsights(
      @RequestParam(required = false, defaultValue = DEFAULT_DATE_PRESET) String datePreset) {
    return service.getDailyInsights(datePreset);
  }

  @GetMapping("/insights/campaigns")
  public Map<String, Object> campaignInsights(
      @RequestParam(required = false, defaultValue = DEFAULT_DATE_PRESET) String datePreset) {
    return service.getCampaignInsights(datePreset);
  }

  @GetMapping("/campaigns")
  public Map<String, Object> campaigns() {
    return service.listCampaigns();
  }

  @ExceptionHandler(MetaAdsClient.MetaApiException.class)
  public ResponseEntity<Map<String, Object>> handleMetaError(MetaAdsClient.MetaApiException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of(
            "error", "meta_api_error",
            "status", e.getStatusCode().value(),
            "details", e.getResponseBody()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return ResponseEntity.status(500).body(Map.of(
        "error", "internal_error",
        "exception", e.getClass().getName(),
        "message", String.valueOf(e.getMessage()),
        "rootCause", root.getClass().getName(),
        "rootMessage", String.valueOf(root.getMessage())));
  }
}
