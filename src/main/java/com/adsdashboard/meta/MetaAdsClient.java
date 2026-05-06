package com.adsdashboard.meta;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

@Component
public class MetaAdsClient {

  private static final String GRAPH_BASE_URL = "https://graph.facebook.com/";
  private static final int DEFAULT_PAGE_LIMIT = 500;

  private final RestClient restClient;
  private final MetaProperties props;

  public MetaAdsClient(MetaProperties props) {
    this.props = props;
    this.restClient = RestClient.builder()
        .baseUrl(GRAPH_BASE_URL + props.apiVersion())
        .messageConverters(converters -> {
          converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
          converters.add(jacksonConverter());
        })
        .build();
  }

  public Map<String, Object> getAdAccountInsights(
      String fields, String datePreset, String level, String timeIncrement) {
    String path = "/act_" + props.adAccountId() + "/insights";
    return get(uri -> {
      UriBuilder b = uri.path(path)
          .queryParam("fields", fields)
          .queryParam("date_preset", datePreset)
          .queryParam("limit", DEFAULT_PAGE_LIMIT)
          .queryParam("access_token", props.accessToken());
      if (level != null && !level.isBlank()) {
        b.queryParam("level", level);
      }
      if (timeIncrement != null && !timeIncrement.isBlank()) {
        b.queryParam("time_increment", timeIncrement);
      }
      return b.build();
    });
  }

  public Map<String, Object> getAdAccountInsightsByTimeRange(
      String fields, String since, String until, String level, String timeIncrement) {
    // time_range JSON 의 {} 가 Spring URI template 으로 오인되지 않도록 URI 직접 빌드.
    String path = "/act_" + props.adAccountId() + "/insights";
    String timeRangeJson = "{\"since\":\"" + since + "\",\"until\":\"" + until + "\"}";
    StringBuilder qs = new StringBuilder();
    qs.append("?fields=").append(enc(fields));
    qs.append("&time_range=").append(enc(timeRangeJson));
    qs.append("&limit=").append(DEFAULT_PAGE_LIMIT);
    qs.append("&access_token=").append(enc(props.accessToken()));
    if (level != null && !level.isBlank()) qs.append("&level=").append(enc(level));
    if (timeIncrement != null && !timeIncrement.isBlank()) {
      qs.append("&time_increment=").append(enc(timeIncrement));
    }
    URI uri = URI.create(GRAPH_BASE_URL + props.apiVersion() + path + qs);
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> body = restClient.get().uri(uri).retrieve().body(Map.class);
      return body;
    } catch (RestClientResponseException e) {
      throw new MetaApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
    }
  }

  private static String enc(String v) {
    return URLEncoder.encode(v, StandardCharsets.UTF_8);
  }

  public Map<String, Object> listCampaigns(String fields) {
    String path = "/act_" + props.adAccountId() + "/campaigns";
    return get(uri -> uri.path(path)
        .queryParam("fields", fields)
        .queryParam("limit", DEFAULT_PAGE_LIMIT)
        .queryParam("access_token", props.accessToken())
        .build());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> get(java.util.function.Function<UriBuilder, java.net.URI> uriFn) {
    try {
      return restClient.get()
          .uri(uriFn::apply)
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw new MetaApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
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

  public static class MetaApiException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String responseBody;

    public MetaApiException(HttpStatusCode statusCode, String responseBody, Throwable cause) {
      super("Meta API error " + statusCode + ": " + responseBody, cause);
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
