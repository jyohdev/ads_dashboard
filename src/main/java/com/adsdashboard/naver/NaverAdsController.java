package com.adsdashboard.naver;

import java.util.Map;
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
}
