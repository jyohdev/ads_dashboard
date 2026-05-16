package com.adsdashboard.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 모든 {@code @RestController} 에서 발생하는 예외를 한곳에서 처리한다.
 *
 * <p>이전에는 매체별 컨트롤러마다 동일한 {@code @ExceptionHandler} 두 개씩(외부 API 에러 +
 * 일반 예외)을 복붙해 두었다. 그 보일러플레이트를 이 클래스 하나로 대체한다 —
 * 새 컨트롤러는 별도 처리 없이 동일한 에러 응답 규격을 자동으로 따른다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 외부 광고 API(Meta·Google·Naver) 호출 실패 — 원본 status·body 를 그대로 내려준다. */
  @ExceptionHandler(AdApiException.class)
  public ResponseEntity<Map<String, Object>> handleAdApi(AdApiException e) {
    log.warn("External ad API error [{}]: {}", e.errorCode(), e.getMessage());
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", e.errorCode());
    body.put("status", e.getStatusCode().value());
    body.put("details", e.getResponseBody());
    return ResponseEntity.status(e.getStatusCode()).body(body);
  }

  /** 그 외 모든 예외 — 500 으로 묶고 루트 원인까지 펼쳐 디버깅 정보를 제공한다. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    log.error("Unhandled exception", e);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", "internal_error");
    body.put("exception", e.getClass().getName());
    body.put("message", String.valueOf(e.getMessage()));
    body.put("rootCause", root.getClass().getName());
    body.put("rootMessage", String.valueOf(root.getMessage()));
    return ResponseEntity.status(500).body(body);
  }
}
