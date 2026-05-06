package com.adsdashboard.naver;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 네이버 캠페인을 (서비스, 채널, 센터)로 분류.
 *
 * 룰:
 *  1) 캠페인명 또는 광고그룹명에 "본사" 또는 "본부" → channel=본사, service는 키워드(방문요양/주간보호)로
 *     결정. 둘 다 없으면 기타.
 *  2) 그 외 service 키워드(방문요양/방요, 주간보호/주보) → channel=센터, center = 분류 키워드 직전 토큰
 *     (이모지/구두점 제거).
 *  3) 매칭 실패 → 기타·미분류.
 *
 * 광고그룹명을 함께 받아 캠페인명만으로 안 잡히는 케이스(센터 단독 캠페인의 service)를 보완.
 */
@Component
public class NaverClassifier {

  public static final String SERVICE_HOMECARE = "방문요양";
  public static final String SERVICE_DAYCARE = "주간보호";
  public static final String SERVICE_OTHER = "기타";

  public static final String CHANNEL_HQ = "본사";
  public static final String CHANNEL_CENTER = "센터";
  public static final String CHANNEL_NONE = "미분류";

  // "본부"는 영남본부/호남본부 같은 지역 본부를 의미 — 센터 채널이지 본사가 아님.
  private static final List<String> HQ_TOKENS = List.of("본사");
  // Order matters — full term first so center extraction anchors on the longer match.
  private static final List<String> HOMECARE_TOKENS = List.of(SERVICE_HOMECARE, "방요");
  private static final List<String> DAYCARE_TOKENS = List.of(SERVICE_DAYCARE, "주보");

  public record Classification(String service, String channel, String center) {}

  public Classification classify(String campaignName, String adGroupName) {
    String camp = campaignName == null ? "" : campaignName;
    String adg = adGroupName == null ? "" : adGroupName;
    String text = (camp + " " + adg).trim();
    if (text.isBlank()) {
      return new Classification(SERVICE_OTHER, CHANNEL_NONE, null);
    }
    if (containsAny(text, HQ_TOKENS)) {
      return new Classification(serviceOf(text), CHANNEL_HQ, null);
    }
    String service = serviceOf(text);
    if (SERVICE_HOMECARE.equals(service) || SERVICE_DAYCARE.equals(service)) {
      // 1) 캠페인명에서 강한 패턴(점/센터/본부/통합/공통) 우선 — 강남센터, 영남본부 같이
      //    캠페인 이름이 진짜 센터를 명시한 케이스
      String center = pickStrongCenter(camp);
      // 2) 없으면 광고그룹의 service 키워드 직전 토큰 (지역명/구·시 등)
      if (center == null) {
        String anchor = anchorOf(text, service);
        center = extractCenterBefore(adg, anchor);
      }
      // 3) 그래도 없으면 캠페인 자체 직전 토큰
      if (center == null) {
        String anchor = anchorOf(text, service);
        center = extractCenterBefore(camp, anchor);
      }
      return new Classification(service, CHANNEL_CENTER, center);
    }
    return new Classification(SERVICE_OTHER, CHANNEL_NONE, null);
  }

  /** 캠페인명 전체에서 ~점/~센터/~본부/~통합/~공통 으로 끝나는 토큰을 우선순위대로 검색. */
  private static String pickStrongCenter(String name) {
    if (name == null || name.isBlank()) return null;
    String[] tokens = name.split("[^\\p{IsHangul}A-Za-z0-9]+");
    for (int i = tokens.length - 1; i >= 0; i--) {
      if (tokens[i].endsWith("점") && tokens[i].length() > 1) return tokens[i];
    }
    for (int i = tokens.length - 1; i >= 0; i--) {
      if (tokens[i].endsWith("센터") && tokens[i].length() > 2) return tokens[i];
    }
    for (int i = tokens.length - 1; i >= 0; i--) {
      if (tokens[i].endsWith("본부") && tokens[i].length() > 2) return tokens[i];
    }
    for (int i = tokens.length - 1; i >= 0; i--) {
      String t = tokens[i];
      if ((t.endsWith("통합") || t.endsWith("공통")) && t.length() > 2) return t;
    }
    return null;
  }

  private static String serviceOf(String text) {
    if (containsAny(text, HOMECARE_TOKENS)) return SERVICE_HOMECARE;
    if (containsAny(text, DAYCARE_TOKENS)) return SERVICE_DAYCARE;
    return SERVICE_OTHER;
  }

  /** Pick the actual token that matched in the text (full term or abbreviation), for slicing. */
  private static String anchorOf(String text, String service) {
    List<String> tokens = SERVICE_HOMECARE.equals(service) ? HOMECARE_TOKENS : DAYCARE_TOKENS;
    for (String t : tokens) {
      if (text.contains(t)) return t;
    }
    return service;
  }

  private static boolean containsAny(String text, List<String> tokens) {
    for (String t : tokens) {
      if (text.contains(t)) return true;
    }
    return false;
  }

  // 광고그룹 이름에 흔히 섞이는 fluff 토큰 — 센터로 잘못 잡히면 안 되므로 fallback에서 스킵.
  private static final Set<String> FLUFF_TOKENS = Set.of(
      "핵심키워드", "대표키워드", "파워링크", "플레이스", "파컨", "브랜드검색",
      "NEW", "홈", "PC", "MO", "본사",
      // Service 키워드는 절대 center 가 될 수 없음
      "방문요양", "주간보호", "방요", "주보", "가족요양");

  private static String extractCenterBefore(String text, String anchor) {
    if (text == null || text.isBlank() || anchor == null) return null;
    int idx = text.indexOf(anchor);
    if (idx <= 0) return null;
    String before = text.substring(0, idx).trim();
    if (before.isEmpty()) return null;
    String[] tokens = before.split("[^\\p{IsHangul}A-Za-z0-9]+");
    // Priority 1: 마지막 ~점 토큰 (안산점, 경주점)
    for (int i = tokens.length - 1; i >= 0; i--) {
      if (tokens[i].endsWith("점") && tokens[i].length() > 1) return tokens[i];
    }
    // Priority 2: 마지막 ~센터 토큰 (강남센터)
    for (int i = tokens.length - 1; i >= 0; i--) {
      if (tokens[i].endsWith("센터") && tokens[i].length() > 2) return tokens[i];
    }
    // Priority 3: 마지막 ~통합/공통 (광주통합, 부산공통)
    for (int i = tokens.length - 1; i >= 0; i--) {
      String t = tokens[i];
      if ((t.endsWith("통합") || t.endsWith("공통")) && t.length() > 2) return t;
    }
    // Priority 4: 마지막 ~시/구 (행정구역 단위로 묶인 케이스)
    for (int i = tokens.length - 1; i >= 0; i--) {
      String t = tokens[i];
      if ((t.endsWith("시") || t.endsWith("구")) && t.length() > 1) return t;
    }
    // Priority 5: 마지막 ~본부 (영남본부, 호남본부 — 지역 단위 본부)
    for (int i = tokens.length - 1; i >= 0; i--) {
      String t = tokens[i];
      if (t.endsWith("본부") && t.length() > 2) return t;
    }
    // 매칭 패턴이 없으면 null — 브랜드 키워드(치매전조증상 등)가 센터로 잘못 잡히는 걸 방지.
    return null;
  }
}
