package com.adsdashboard.naver;

import static org.assertj.core.api.Assertions.assertThat;

import com.adsdashboard.naver.NaverClassifier.Classification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 온라인(네이버) 캠페인명 → 서비스 / 채널(본사·센터) / 센터 분류 검증.
 *
 * <p>Meta·Google 의 본부/센터 매핑은 프런트(v2.html)의 JS 로직이라 이 테스트 범위 밖이다.
 */
@DisplayName("NaverClassifier — 온라인 캠페인명 매핑")
class NaverClassifierTest {

  private final NaverClassifier classifier = new NaverClassifier();

  @Nested
  @DisplayName("서비스 분류")
  class ServiceClassification {

    @Test
    @DisplayName("방문요양 / 방요 키워드 → 방문요양")
    void homecare() {
      assertThat(classifier.classify("방문요양 안산점", "").service())
          .isEqualTo(NaverClassifier.SERVICE_HOMECARE);
      assertThat(classifier.classify("방요 경주점 플레이스", "").service())
          .isEqualTo(NaverClassifier.SERVICE_HOMECARE);
    }

    @Test
    @DisplayName("주간보호 / 주보 키워드 → 주간보호")
    void daycare() {
      assertThat(classifier.classify("주간보호 부천점", "").service())
          .isEqualTo(NaverClassifier.SERVICE_DAYCARE);
      assertThat(classifier.classify("주보 안산점 파워링크", "").service())
          .isEqualTo(NaverClassifier.SERVICE_DAYCARE);
    }

    @Test
    @DisplayName("서비스 키워드가 없으면 기타")
    void other() {
      assertThat(classifier.classify("치매 전조증상 정보", "").service())
          .isEqualTo(NaverClassifier.SERVICE_OTHER);
    }
  }

  @Nested
  @DisplayName("채널 분류 (본사 vs 센터)")
  class ChannelClassification {

    @Test
    @DisplayName("'본사' 키워드가 있으면 본사 채널 (center 는 null)")
    void headquarters() {
      Classification c = classifier.classify("케어링 본사 방문요양 브랜드검색", "");
      assertThat(c.channel()).isEqualTo(NaverClassifier.CHANNEL_HQ);
      assertThat(c.service()).isEqualTo(NaverClassifier.SERVICE_HOMECARE);
      assertThat(c.center()).isNull();
    }

    @Test
    @DisplayName("서비스 키워드 + 센터명 → 센터 채널")
    void center() {
      assertThat(classifier.classify("방문요양 부천점", "").channel())
          .isEqualTo(NaverClassifier.CHANNEL_CENTER);
    }

    @Test
    @DisplayName("매칭 실패 시 미분류")
    void none() {
      Classification c = classifier.classify("치매 전조증상", "");
      assertThat(c.channel()).isEqualTo(NaverClassifier.CHANNEL_NONE);
      assertThat(c.center()).isNull();
    }

    @Test
    @DisplayName("빈 캠페인명은 기타·미분류")
    void blank() {
      Classification c = classifier.classify("", "");
      assertThat(c.service()).isEqualTo(NaverClassifier.SERVICE_OTHER);
      assertThat(c.channel()).isEqualTo(NaverClassifier.CHANNEL_NONE);
    }
  }

  @Nested
  @DisplayName("센터 추출")
  class CenterExtraction {

    @Test
    @DisplayName("~점 / ~센터 / ~본부 로 끝나는 토큰을 센터로 추출")
    void strongCenterTokens() {
      assertThat(classifier.classify("방문요양 부천점", "").center()).isEqualTo("부천점");
      assertThat(classifier.classify("방문요양 강남센터", "").center()).isEqualTo("강남센터");
      assertThat(classifier.classify("방문요양 영남본부", "").center()).isEqualTo("영남본부");
    }

    @Test
    @DisplayName("단독 suffix 토큰(센터·본부 등)은 센터로 채택하지 않는다")
    void bareSuffixIsNotCenter() {
      assertThat(classifier.classify("방문요양 센터", "").center()).isNull();
    }

    @Test
    @DisplayName("캠페인명에 센터가 없으면 광고그룹명에서 보완 추출")
    void centerFromAdGroupName() {
      Classification c = classifier.classify("주간보호", "부천점 주간보호 대표키워드");
      assertThat(c.service()).isEqualTo(NaverClassifier.SERVICE_DAYCARE);
      assertThat(c.center()).isEqualTo("부천점");
    }
  }
}
