package com.adsdashboard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 스프링 컨텍스트가 정상적으로 떠오르는지 확인하는 스모크 테스트.
 *
 * <p>OAuth client-id 가 비어 있으면 SecurityConfig 빈 생성이 실패하므로,
 * 테스트에서는 더미 OAuth 자격증명을 주입한다. (실제 값은 운영 환경변수로 주입)
 */
@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class AdsDashboardApplicationTests {

  @Test
  void contextLoads() {}
}
