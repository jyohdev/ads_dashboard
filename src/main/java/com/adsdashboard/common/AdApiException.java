package com.adsdashboard.common;

import org.springframework.http.HttpStatusCode;

/**
 * 외부 광고 API(Meta·Google Ads·Naver) 호출 실패를 표현하는 공통 예외.
 *
 * <p>매체별 클라이언트는 이 클래스를 상속한 예외(MetaApiException 등)를 던지고,
 * {@link GlobalExceptionHandler} 가 이 타입 하나로 세 매체의 에러 응답을 일관되게 변환한다.
 * 덕분에 컨트롤러마다 흩어져 있던 동일한 {@code @ExceptionHandler} 보일러플레이트가 사라진다.
 */
public abstract class AdApiException extends RuntimeException {

  private final HttpStatusCode statusCode;
  private final String responseBody;

  protected AdApiException(
      String platform, HttpStatusCode statusCode, String responseBody, Throwable cause) {
    super(platform + " API error " + statusCode + ": " + responseBody, cause);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  /** 클라이언트로 내려갈 에러 식별 코드 (예: {@code "meta_api_error"}). */
  public abstract String errorCode();

  /** 외부 API 가 돌려준 원본 HTTP 상태 — 대시보드 응답에도 그대로 전달한다. */
  public HttpStatusCode getStatusCode() {
    return statusCode;
  }

  /** 외부 API 가 돌려준 원본 응답 바디 — 디버깅을 위해 그대로 전달한다. */
  public String getResponseBody() {
    return responseBody;
  }
}
