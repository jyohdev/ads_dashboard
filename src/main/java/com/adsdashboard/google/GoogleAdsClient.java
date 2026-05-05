package com.adsdashboard.google;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GoogleAdsClient {

  private static final String OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
  private static final String GOOGLE_ADS_BASE_URL = "https://googleads.googleapis.com/";
  private static final Duration TOKEN_EXPIRY_BUFFER = Duration.ofSeconds(60);

  private final RestClient oauthClient;
  private final RestClient adsClient;
  private final GoogleProperties props;

  private final Object tokenLock = new Object();
  private volatile String cachedAccessToken;
  private volatile Instant cachedExpiry = Instant.EPOCH;

  public GoogleAdsClient(GoogleProperties props) {
    this.props = props;
    this.oauthClient = RestClient.builder()
        .baseUrl(OAUTH_TOKEN_URL)
        .messageConverters(converters -> {
          converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
          converters.add(jacksonConverter());
        })
        .build();
    this.adsClient = RestClient.builder()
        .baseUrl(GOOGLE_ADS_BASE_URL + props.apiVersion())
        .messageConverters(converters -> {
          converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
          converters.add(jacksonConverter());
        })
        .build();
  }

  public Map<String, Object> search(String gaqlQuery) {
    String accessToken = getAccessToken();
    String path = "/customers/" + props.customerId() + "/googleAds:search";
    try {
      return adsClient.post()
          .uri(path)
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + accessToken)
          .header("developer-token", props.developerToken())
          .headers(headers -> {
            if (props.loginCustomerId() != null && !props.loginCustomerId().isBlank()) {
              headers.set("login-customer-id", props.loginCustomerId());
            }
          })
          .body(Map.of("query", gaqlQuery))
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw new GoogleAdsApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
    }
  }

  private String getAccessToken() {
    if (cachedAccessToken != null && Instant.now().isBefore(cachedExpiry)) {
      return cachedAccessToken;
    }
    synchronized (tokenLock) {
      if (cachedAccessToken != null && Instant.now().isBefore(cachedExpiry)) {
        return cachedAccessToken;
      }
      Map<String, Object> resp = refreshAccessToken();
      String token = (String) resp.get("access_token");
      Number expiresIn = (Number) resp.get("expires_in");
      long ttl = expiresIn != null ? expiresIn.longValue() : 3600L;
      cachedAccessToken = token;
      cachedExpiry = Instant.now().plusSeconds(ttl).minus(TOKEN_EXPIRY_BUFFER);
      return token;
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> refreshAccessToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", props.clientId());
    form.add("client_secret", props.clientSecret());
    form.add("refresh_token", props.refreshToken());
    form.add("grant_type", "refresh_token");
    try {
      return oauthClient.post()
          .uri("")
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw new GoogleAdsApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
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

  public static class GoogleAdsApiException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String responseBody;

    public GoogleAdsApiException(HttpStatusCode statusCode, String responseBody, Throwable cause) {
      super("Google Ads API error " + statusCode + ": " + responseBody, cause);
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
