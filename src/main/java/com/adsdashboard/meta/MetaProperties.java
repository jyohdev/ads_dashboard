package com.adsdashboard.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meta")
public record MetaProperties(String accessToken, String adAccountId, String apiVersion) {

  private static final String DEFAULT_API_VERSION = "v21.0";

  public MetaProperties {
    if (apiVersion == null || apiVersion.isBlank()) {
      apiVersion = DEFAULT_API_VERSION;
    }
  }
}
