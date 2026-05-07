package com.adsdashboard.lead;

import java.time.LocalDate;

/**
 * 신규콜 한 건. CSV 한 row = 한 건의 상담.
 *
 * 필드 중 daycareCenter / homecareCenter / hqOnly 셋 중 하나만 채워짐(또는 hq=null + center 만):
 *  - daycareCenter 채워짐 → 주간보호 신규콜
 *  - homecareCenter 채워짐 → 방문요양 신규콜
 *  - 둘 다 비고 hq 만 → 본사 신규콜
 */
public record LeadEntry(
    LocalDate date,
    String hq,
    String daycareCenter,
    String homecareCenter) {

  public String service() {
    if (homecareCenter != null && !homecareCenter.isBlank()) return "방문요양";
    if (daycareCenter != null && !daycareCenter.isBlank()) return "주간보호";
    return "본사";
  }

  public String center() {
    if (homecareCenter != null && !homecareCenter.isBlank()) return homecareCenter;
    if (daycareCenter != null && !daycareCenter.isBlank()) return daycareCenter;
    return null;
  }
}
