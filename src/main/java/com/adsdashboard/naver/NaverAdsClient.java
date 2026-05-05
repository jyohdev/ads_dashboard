package com.adsdashboard.naver;

import com.adsdashboard.naver.NaverProperties.NaverAccount;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NaverAdsClient {

  private static final String HMAC_ALGO = "HmacSHA256";

  private final RestClient restClient;

  public NaverAdsClient(NaverProperties props) {
    this.restClient = RestClient.builder()
        .baseUrl(props.baseUrl())
        .messageConverters(converters -> {
          converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
          converters.add(jacksonConverter());
        })
        .build();
  }

  public List<Map<String, Object>> listCampaigns(NaverAccount account) {
    return getList(account, "/ncc/campaigns", null);
  }

  public List<Map<String, Object>> listAdGroups(NaverAccount account, String campaignId) {
    return getList(account, "/ncc/adgroups", "nccCampaignId=" + campaignId);
  }

  public Map<String, Object> getStats(
      NaverAccount account, List<String> ids, String fieldsJson, String timeRangeJson) {
    StringBuilder qs = new StringBuilder();
    qs.append("ids=").append(String.join(",", ids));
    qs.append("&fields=").append(urlEncode(fieldsJson));
    qs.append("&timeRange=").append(urlEncode(timeRangeJson));
    return getMap(account, "/stats", qs.toString());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> getList(
      NaverAccount account, String path, String queryString) {
    try {
      return restClient.get()
          .uri(uriBuilder -> {
            uriBuilder.path(path);
            if (queryString != null && !queryString.isBlank()) {
              uriBuilder.query(queryString);
            }
            return uriBuilder.build();
          })
          .headers(headers -> applyAuthHeaders(headers, account, "GET", path))
          .retrieve()
          .body(List.class);
    } catch (RestClientResponseException e) {
      throw new NaverAdsApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getMap(NaverAccount account, String path, String queryString) {
    try {
      return restClient.get()
          .uri(uriBuilder -> {
            uriBuilder.path(path);
            if (queryString != null && !queryString.isBlank()) {
              uriBuilder.query(queryString);
            }
            return uriBuilder.build();
          })
          .headers(headers -> applyAuthHeaders(headers, account, "GET", path))
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw new NaverAdsApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
    }
  }

  private static void applyAuthHeaders(
      org.springframework.http.HttpHeaders headers,
      NaverAccount account,
      String method,
      String uriPath) {
    String timestamp = String.valueOf(System.currentTimeMillis());
    String signature = sign(account.secretKey(), timestamp, method, uriPath);
    headers.set("X-Timestamp", timestamp);
    headers.set("X-API-KEY", account.apiKey());
    headers.set("X-Customer", account.customerId());
    headers.set("X-Signature", signature);
  }

  private static String sign(String secretKey, String timestamp, String method, String uriPath) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
      String message = timestamp + "." + method + "." + uriPath;
      byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign Naver Ads request", e);
    }
  }

  private static String urlEncode(String s) {
    return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static MappingJackson2HttpMessageConverter jacksonConverter() {
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter.setSupportedMediaTypes(List.of(
        MediaType.APPLICATION_JSON,
        MediaType.parseMediaType("text/javascript"),
        MediaType.parseMediaType("text/plain"),
        MediaType.parseMediaType("application/*+json")));
    return converter;
  }

  public static class NaverAdsApiException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String responseBody;

    public NaverAdsApiException(HttpStatusCode statusCode, String responseBody, Throwable cause) {
      super("Naver Ads API error " + statusCode + ": " + responseBody, cause);
      this.statusCode = statusCode;
      this.responseBody = responseBody;
    }

    public HttpStatusCode getStatusCode() {
      return statusCode;
    }

    public String getResponseBody() {
      return responseBody;
    }
  }
}
