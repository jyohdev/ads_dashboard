package com.adsdashboard.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google")
public record GoogleProperties(
    String developerToken,
    String clientId,
    String clientSecret,
    String refreshToken,
    String customerId,
    String loginCustomerId,
    String apiVersion) {

  private static final String DEFAULT_API_VERSION = "v20";

  public GoogleProperties {
    if (apiVersion == null || apiVersion.isBlank()) {
      apiVersion = DEFAULT_API_VERSION;
    }
  }
}
