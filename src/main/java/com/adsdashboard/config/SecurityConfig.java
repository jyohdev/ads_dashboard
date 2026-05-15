package com.adsdashboard.config;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  private static final Set<String> PUBLIC_PATHS = Set.of(
      "/actuator/health", "/actuator/info", "/error", "/login", "/login.html", "/favicon.ico", "/logo.png");

  @Value("${app.allowed-email-domain:caring.co.kr}")
  private String allowedDomain;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(PUBLIC_PATHS.toArray(new String[0])).permitAll()
            .requestMatchers("/oauth2/**", "/login/**").permitAll()
            .anyRequest().authenticated())
        .oauth2Login(o -> o
            .loginPage("/login.html")
            .failureUrl("/login.html?error")
            .defaultSuccessUrl("/v2.html", true)
            .userInfoEndpoint(u -> u.oidcUserService(domainRestrictedOidcUserService())));
    return http.build();
  }

  private OAuth2UserService<OidcUserRequest, OidcUser> domainRestrictedOidcUserService() {
    OidcUserService delegate = new OidcUserService();
    return userRequest -> {
      OidcUser user = delegate.loadUser(userRequest);
      String email = user.getEmail();
      if (email == null || !email.toLowerCase().endsWith("@" + allowedDomain.toLowerCase())) {
        OAuth2Error err = new OAuth2Error(
            "domain_not_allowed",
            "Only @" + allowedDomain + " accounts are allowed.",
            null);
        throw new OAuth2AuthenticationException(err, err.getDescription());
      }
      return user;
    };
  }
}
