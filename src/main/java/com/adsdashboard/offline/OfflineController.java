package com.adsdashboard.offline;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offline")
public class OfflineController {

  private final OfflineService service;

  public OfflineController(OfflineService service) {
    this.service = service;
  }

  @GetMapping("/by-category")
  public Map<String, Object> byCategory(
      @RequestParam(required = false, defaultValue = "last_7d") String datePreset,
      @RequestParam(required = false) String since,
      @RequestParam(required = false) String until,
      @RequestParam(required = false, defaultValue = "all") String hq,
      @RequestParam(required = false, defaultValue = "all") String center) {
    return service.getByCategory(datePreset, since, until, hq, center);
  }
}
