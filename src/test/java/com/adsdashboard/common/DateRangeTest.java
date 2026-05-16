package com.adsdashboard.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** datePreset → [since, until] 기간 환산이 정확한지 검증. */
@DisplayName("DateRange — 기간(datePreset) 환산")
class DateRangeTest {

  @Test
  @DisplayName("today 는 오늘 하루")
  void today() {
    DateRange r = DateRange.resolve("today", null, null);
    assertThat(r.since()).isEqualTo(LocalDate.now());
    assertThat(r.until()).isEqualTo(LocalDate.now());
  }

  @Test
  @DisplayName("yesterday 는 어제 하루")
  void yesterday() {
    LocalDate y = LocalDate.now().minusDays(1);
    DateRange r = DateRange.resolve("yesterday", null, null);
    assertThat(r.since()).isEqualTo(y);
    assertThat(r.until()).isEqualTo(y);
  }

  @Test
  @DisplayName("last_7d 는 오늘 포함 7일 (오늘 - 6 ~ 오늘)")
  void last7d() {
    DateRange r = DateRange.resolve("last_7d", null, null);
    assertThat(r.until()).isEqualTo(LocalDate.now());
    assertThat(r.since()).isEqualTo(LocalDate.now().minusDays(6));
  }

  @Test
  @DisplayName("last_14d / last_30d 는 오늘 포함 N일")
  void last14dAnd30d() {
    assertThat(DateRange.resolve("last_14d", null, null).since())
        .isEqualTo(LocalDate.now().minusDays(13));
    assertThat(DateRange.resolve("last_30d", null, null).since())
        .isEqualTo(LocalDate.now().minusDays(29));
  }

  @Test
  @DisplayName("this_month 는 이번 달 1일 ~ 오늘")
  void thisMonth() {
    DateRange r = DateRange.resolve("this_month", null, null);
    assertThat(r.since()).isEqualTo(LocalDate.now().withDayOfMonth(1));
    assertThat(r.until()).isEqualTo(LocalDate.now());
  }

  @Test
  @DisplayName("last_month 는 지난달 1일 ~ 지난달 말일")
  void lastMonth() {
    LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);
    DateRange r = DateRange.resolve("last_month", null, null);
    assertThat(r.since()).isEqualTo(firstOfThisMonth.minusMonths(1));
    assertThat(r.until()).isEqualTo(firstOfThisMonth.minusDays(1));
  }

  @Test
  @DisplayName("since·until 을 둘 다 주면 preset 을 무시하고 커스텀 범위 사용")
  void customRange() {
    DateRange r = DateRange.resolve("last_7d", "2026-01-01", "2026-01-31");
    assertThat(r.since()).isEqualTo(LocalDate.parse("2026-01-01"));
    assertThat(r.until()).isEqualTo(LocalDate.parse("2026-01-31"));
  }

  @ParameterizedTest(name = "preset=\"{0}\" → last_7d 로 기본 처리")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "존재하지않는프리셋"})
  @DisplayName("null·공백·미지원 preset 은 last_7d 로 폴백")
  void unknownPresetFallsBackToLast7d(String preset) {
    DateRange r = DateRange.resolve(preset, null, null);
    assertThat(r.until()).isEqualTo(LocalDate.now());
    assertThat(r.since()).isEqualTo(LocalDate.now().minusDays(6));
  }

  @Test
  @DisplayName("contains 는 양 끝(since·until)을 포함한다")
  void containsIsInclusive() {
    DateRange r = new DateRange(LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31"));
    assertThat(r.contains(LocalDate.parse("2026-05-01"))).isTrue();   // 시작일 포함
    assertThat(r.contains(LocalDate.parse("2026-05-15"))).isTrue();   // 범위 안
    assertThat(r.contains(LocalDate.parse("2026-05-31"))).isTrue();   // 종료일 포함
    assertThat(r.contains(LocalDate.parse("2026-04-30"))).isFalse();  // 하루 전
    assertThat(r.contains(LocalDate.parse("2026-06-01"))).isFalse();  // 하루 후
  }
}
