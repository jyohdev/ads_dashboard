package com.adsdashboard.naver;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver")
public record NaverProperties(String baseUrl, List<NaverAccount> accounts) {

  private static final String DEFAULT_BASE_URL = "https://api.searchad.naver.com";

  public NaverProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = DEFAULT_BASE_URL;
    }
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
  }

  public List<NaverAccount> activeAccounts() {
    return accounts.stream().filter(NaverAccount::isConfigured).toList();
  }

  public record NaverAccount(String name, String apiKey, String secretKey, String customerId) {

    public boolean isConfigured() {
      return notBlank(apiKey) && notBlank(secretKey) && notBlank(customerId);
    }

    private static boolean notBlank(String s) {
      return s != null && !s.isBlank();
    }
  }
}
