package com.adsdashboard.meta;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/meta")
public class MetaAdsController {

    private final MetaAdsService service;

    public MetaAdsController(MetaAdsService service) {
        this.service = service;
    }

    @GetMapping("/insights")
    public Map<String, Object> insights(
            @RequestParam(required = false, defaultValue = "last_7d") String datePreset) {
        return service.getInsights(datePreset);
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
                        "details", e.getResponseBody()
                ));
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
                "rootMessage", String.valueOf(root.getMessage())
        ));
    }
}
