package com.adsdashboard.lead;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 시트 raw 센터명 → 대시보드 공식 센터명 정규화 검증. */
@DisplayName("CenterNameNormalizer — 센터명 정규화")
class CenterNameNormalizerTest {

  @Test
  @DisplayName("병설 suffix 누락 별칭을 공식 명으로 보정")
  void byeolseolAlias() {
    assertThat(CenterNameNormalizer.normalize("양산점")).isEqualTo("양산점(병설)");
    assertThat(CenterNameNormalizer.normalize("부산사하점")).isEqualTo("부산사하점(병설)");
    assertThat(CenterNameNormalizer.normalize("봄날점")).isEqualTo("광주봄날점(병설)");
  }

  @Test
  @DisplayName("행정구·구센터명 별칭을 신 명칭으로 보정")
  void districtAndLegacyAlias() {
    assertThat(CenterNameNormalizer.normalize("양천구")).isEqualTo("서울양천점");
    assertThat(CenterNameNormalizer.normalize("강남센터")).isEqualTo("(신)강남점");
    assertThat(CenterNameNormalizer.normalize("고은센터")).isEqualTo("안양점");
  }

  @Test
  @DisplayName("앞뒤 공백은 제거 후 매칭")
  void trimsBeforeMatching() {
    assertThat(CenterNameNormalizer.normalize("  양산점 ")).isEqualTo("양산점(병설)");
  }

  @Test
  @DisplayName("별칭이 없으면 입력값을 그대로 반환")
  void passthroughWhenNoAlias() {
    assertThat(CenterNameNormalizer.normalize("부천점")).isEqualTo("부천점");
  }

  @Test
  @DisplayName("null 은 null 그대로")
  void nullStaysNull() {
    assertThat(CenterNameNormalizer.normalize(null)).isNull();
  }
}
