package com.adsdashboard.lead;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 신규콜 1건의 서비스·센터 분류 검증.
 *
 * <p>daycareCenter / homecareCenter 중 채워진 쪽으로 서비스가 결정되고,
 * 둘 다 비어 있으면 본사 신규콜로 분류된다.
 */
@DisplayName("LeadEntry — 신규콜 서비스/센터 분류")
class LeadEntryTest {

  private static final LocalDate D = LocalDate.of(2026, 5, 16);

  @Test
  @DisplayName("homecareCenter 가 채워지면 방문요양")
  void homecare() {
    LeadEntry e = new LeadEntry(D, "수도권1본부", null, "강남점");
    assertThat(e.service()).isEqualTo("방문요양");
    assertThat(e.center()).isEqualTo("강남점");
  }

  @Test
  @DisplayName("daycareCenter 가 채워지면 주간보호")
  void daycare() {
    LeadEntry e = new LeadEntry(D, "수도권2본부", "부천점", null);
    assertThat(e.service()).isEqualTo("주간보호");
    assertThat(e.center()).isEqualTo("부천점");
  }

  @Test
  @DisplayName("센터가 둘 다 비면 본사 신규콜 (center 는 null)")
  void headquarters() {
    LeadEntry e = new LeadEntry(D, "본사", null, null);
    assertThat(e.service()).isEqualTo("본사");
    assertThat(e.center()).isNull();
  }

  @Test
  @DisplayName("두 센터가 모두 있으면 방문요양(homecare)이 우선")
  void homecareTakesPrecedence() {
    LeadEntry e = new LeadEntry(D, "수도권3본부", "부천점", "강남점");
    assertThat(e.service()).isEqualTo("방문요양");
    assertThat(e.center()).isEqualTo("강남점");
  }
}
