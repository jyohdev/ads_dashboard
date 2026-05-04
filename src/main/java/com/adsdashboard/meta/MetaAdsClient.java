package com.adsdashboard.meta;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class MetaAdsClient {

    private final RestClient restClient;
    private final MetaProperties props;

    public MetaAdsClient(MetaProperties props) {
        this.props = props;
        MappingJackson2HttpMessageConverter jackson = new MappingJackson2HttpMessageConverter();
        jackson.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.parseMediaType("text/javascript"),
                MediaType.parseMediaType("text/plain"),
                MediaType.parseMediaType("application/*+json")
        ));
        this.restClient = RestClient.builder()
                .baseUrl("https://graph.facebook.com/" + props.apiVersion())
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(jackson);
                })
                .build();
    }

    public Map<String, Object> getAdAccountInsights(String fields, String datePreset) {
        String path = "/act_" + props.adAccountId() + "/insights";
        try {
            return restClient.get()
                    .uri(uri -> uri.path(path)
                            .queryParam("fields", fields)
                            .queryParam("date_preset", datePreset)
                            .queryParam("access_token", props.accessToken())
                            .build())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw new MetaApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
        }
    }

    public Map<String, Object> listCampaigns(String fields) {
        String path = "/act_" + props.adAccountId() + "/campaigns";
        try {
            return restClient.get()
                    .uri(uri -> uri.path(path)
                            .queryParam("fields", fields)
                            .queryParam("access_token", props.accessToken())
                            .build())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw new MetaApiException(e.getStatusCode(), e.getResponseBodyAsString(), e);
        }
    }

    public static class MetaApiException extends RuntimeException {
        private final HttpStatusCode statusCode;
        private final String responseBody;

        public MetaApiException(HttpStatusCode statusCode, String responseBody, Throwable cause) {
            super("Meta API error " + statusCode + ": " + responseBody, cause);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public HttpStatusCode getStatusCode() { return statusCode; }
        public String getResponseBody() { return responseBody; }
    }
}
