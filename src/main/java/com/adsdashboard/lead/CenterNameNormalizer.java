package com.adsdashboard.lead;

import java.util.Map;

/**
 * 시트에서 들어오는 raw 센터명을 대시보드 공식 명(HQ_CENTERS와 동일)으로 정규화.
 *
 * 프런트(v2.html)의 CENTER_NORMALIZE 와 의도적으로 같은 매핑을 유지한다.
 * 필터링/집계가 양쪽에서 일관된 키로 동작해야 하기 때문.
 */
final class CenterNameNormalizer {

  private CenterNameNormalizer() {}

  private static final Map<String, String> ALIAS = Map.ofEntries(
      // 병설 suffix 누락
      Map.entry("양산점", "양산점(병설)"),
      Map.entry("부산사하점", "부산사하점(병설)"),
      Map.entry("사하점", "부산사하점(병설)"),
      Map.entry("군산점", "군산점(병설)"),
      Map.entry("대전둔산점", "대전둔산점(병설)"),
      Map.entry("광주봄날점", "광주봄날점(병설)"),
      Map.entry("봄날점", "광주봄날점(병설)"),
      // 서울 prefix 누락 / 행정구 표기
      Map.entry("강남센터", "(신)강남점"),
      Map.entry("서울성북점", "성북점"),
      Map.entry("서울영등포점", "영등포점"),
      Map.entry("은평구", "서울은평점"),
      Map.entry("도봉구", "서울도봉점"),
      Map.entry("양천구", "서울양천점"),
      Map.entry("안양시", "안양점"),
      // 구 센터명(가온/고은/다솜/라온/서초) → 신 명칭 (2026-05-16)
      Map.entry("서초센터", "서울서초점"),
      Map.entry("가온센터", "서울도봉점"),
      Map.entry("고은센터", "안양점"),
      Map.entry("다솜센터", "서울은평점"),
      Map.entry("라온센터", "서울양천점")
  );

  public static String normalize(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    return ALIAS.getOrDefault(trimmed, trimmed);
  }
}
