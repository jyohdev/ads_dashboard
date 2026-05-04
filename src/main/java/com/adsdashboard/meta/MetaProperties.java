package com.adsdashboard.meta;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meta")
public record MetaProperties(
        String accessToken,
        String adAccountId,
        String apiVersion
) {
    public MetaProperties {
        if (apiVersion == null || apiVersion.isBlank()) {
            apiVersion = "v21.0";
        }
    }
}
