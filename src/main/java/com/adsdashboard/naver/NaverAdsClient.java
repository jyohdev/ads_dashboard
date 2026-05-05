package com.adsdashboard.naver;

import com.adsdashboard.naver.NaverProperties.NaverAccount;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
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
  private final String baseUrl;

  public NaverAdsClient(NaverProperties props) {
    this.baseUrl = stripTrailingSlash(props.baseUrl());
    this.restClient = RestClient.builder()
        .messageConverters(converters -> {
          converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
          converters.add(jacksonConverter());
        })
        .build();
  }

  public List<Map<String, Object>> listCampaigns(NaverAccount account) {
    return getList(account, "/ncc/campaigns", Map.of());
  }

  public List<Map<String, Object>> listAdGroups(NaverAccount account, String campaignId) {
    return getList(account, "/ncc/adgroups", Map.of("nccCampaignId", campaignId));
  }

  public Map<String, Object> getStatsByDatePreset(
      NaverAccount account, List<String> ids, String fieldsJson, String datePreset) {
    Map<String, String> qp = new LinkedHashMap<>();
    qp.put("ids", String.join(",", ids));
    qp.put("fields", fieldsJson);
    qp.put("datePreset", datePreset);
    qp.put("timeIncrement", "allDays");
    return getMap(account, "/stats", qp);
  }

  public Map<String, Object> getStatsByTimeRange(
      NaverAccount account, List<String> ids, String fieldsJson, String timeRangeJson) {
    Map<String, String> qp = new LinkedHashMap<>();
    qp.put("ids", String.join(",", ids));
    qp.put("fields", fieldsJson);
    qp.put("timeRange", timeRangeJson);
    qp.put("timeIncrement", "allDays");
    return getMap(account, "/stats", qp);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> getList(
      NaverAccount account, String path, Map<String, String> params) {
    URI uri = URI.create(baseUrl + path + buildQuery(params));
    try {
      return restClient.get()
          .uri(uri)
          .headers(headers -> applyAuthHeaders(headers, account, "GET", path))
          .retrieve()
          .body(List.class);
    } catch (RestClientResponseException e) {
      throw new NaverAdsApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getMap(NaverAccount account, String path, Map<String, String> params) {
    URI uri = URI.create(baseUrl + path + buildQuery(params));
    try {
      return restClient.get()
          .uri(uri)
          .headers(headers -> applyAuthHeaders(headers, account, "GET", path))
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw new NaverAdsApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
    }
  }

  private static String buildQuery(Map<String, String> params) {
    if (params == null || params.isEmpty()) return "";
    StringBuilder sb = new StringBuilder("?");
    boolean first = true;
    for (Map.Entry<String, String> e : params.entrySet()) {
      if (!first) sb.append('&');
      sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
      first = false;
    }
    return sb.toString();
  }

  private static String stripTrailingSlash(String s) {
    return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
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
