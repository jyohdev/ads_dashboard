package com.adsdashboard.cac;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cac")
public class CacController {

  private final CacService service;

  public CacController(CacService service) { this.service = service; }

  @GetMapping
  public Map<String, Object> get() {
    return service.getAll();
  }
}
