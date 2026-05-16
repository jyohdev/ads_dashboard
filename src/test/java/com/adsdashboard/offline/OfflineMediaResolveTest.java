package com.adsdashboard.offline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 오프라인 광고 매체 분류 검증 — {@code OfflineSheetFetcher.resolveMedia}.
 *
 * <p>우선순위: ① 오프라인유입 컬럼 값 → ② 비고에서 키워드 추출 → ③ 종류 라벨 → ④ "기타".
 */
@DisplayName("OfflineSheetFetcher.resolveMedia — 오프라인 매체 매핑")
class OfflineMediaResolveTest {

  @Test
  @DisplayName("① 오프라인유입 컬럼이 명시되면 그 값을 그대로 사용")
  void explicitMediaColumnWins() {
    assertThat(OfflineSheetFetcher.resolveMedia("현수막", "비고 내용", "오프라인"))
        .isEqualTo("현수막");
  }

  @Test
  @DisplayName("② 컬럼이 '기타'면 비고에서 키워드를 추출")
  void keywordFromNoteWhenColumnIsEtc() {
    assertThat(OfflineSheetFetcher.resolveMedia("기타", "아파트 단지 현수막 게시", "오프라인"))
        .isEqualTo("현수막");
  }

  @Test
  @DisplayName("② 컬럼이 비어 있어도 비고에서 키워드를 추출")
  void keywordFromNoteWhenColumnIsBlank() {
    assertThat(OfflineSheetFetcher.resolveMedia(null, "부채 1,000개 제작 배포", "판촉물"))
        .isEqualTo("부채");
  }

  @Test
  @DisplayName("③ 키워드 매칭 실패 시 종류(센터지원·판촉물 등)를 라벨로 사용")
  void fallsBackToKindLabel() {
    assertThat(OfflineSheetFetcher.resolveMedia(null, "별다른 키워드 없는 메모", "센터지원"))
        .isEqualTo("센터지원");
  }

  @Test
  @DisplayName("④ 아무것도 못 찾고 종류가 '오프라인'뿐이면 '기타'")
  void fallsBackToEtc() {
    assertThat(OfflineSheetFetcher.resolveMedia(null, null, "오프라인"))
        .isEqualTo("기타");
  }
}
