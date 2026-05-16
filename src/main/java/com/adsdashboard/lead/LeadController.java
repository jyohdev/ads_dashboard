package com.adsdashboard.lead;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

  private final LeadService service;

  public LeadController(LeadService service) {
    this.service = service;
  }

  @GetMapping("/by-category")
  public Map<String, Object> byCategory(
      @RequestParam(required = false, defaultValue = "last_7d") String datePreset,
      @RequestParam(required = false) String since,
      @RequestParam(required = false) String until,
      @RequestParam(required = false) String hq,
      @RequestParam(required = false) String center) {
    return service.getByCategory(datePreset, since, until, hq, center);
  }
}
