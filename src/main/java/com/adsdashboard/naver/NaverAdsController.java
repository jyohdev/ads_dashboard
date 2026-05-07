package com.adsdashboard.naver;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/naver")
public class NaverAdsController {

  private static final String DEFAULT_DATE_PRESET = "last_7d";

  private final NaverAdsService service;

  public NaverAdsController(NaverAdsService service) {
    this.service = service;
  }

  @GetMapping("/campaigns")
  public Map<String, Object> campaigns() {
    return service.listCampaigns();
  }

  @GetMapping("/adgroups")
  public Map<String, Object> adGroups() {
    return service.listAdGroups();
  }

  @GetMapping("/insights/campaigns")
  public Map<String, Object> campaignInsights(
      @RequestParam(required = false, defaultValue = DEFAULT_DATE_PRESET) String datePreset,
      @RequestParam(required = false) String since,
      @RequestParam(required = false) String until) {
    return service.getCampaignInsights(datePreset, since, until);
  }

  @GetMapping("/insights/adgroups")
  public Map<String, Object> adGroupInsights(
      @RequestParam(required = false, defaultValue = DEFAULT_DATE_PRESET) String datePreset,
      @RequestParam(required = false) String since,
      @RequestParam(required = false) String until) {
    return service.getAdGroupInsights(datePreset, since, until);
  }

  @GetMapping("/insights/by-category")
  public Map<String, Object> insightsByCategory(
      @RequestParam(required = false, defaultValue = DEFAULT_DATE_PRESET) String datePreset,
      @RequestParam(required = false) String since,
      @RequestParam(required = false) String until) {
    return service.getInsightsByCategory(datePreset, since, until);
  }

  @ExceptionHandler(NaverAdsClient.NaverAdsApiException.class)
  public ResponseEntity<Map<String, Object>> handleNaverError(
      NaverAdsClient.NaverAdsApiException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of(
            "error", "naver_ads_api_error",
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
