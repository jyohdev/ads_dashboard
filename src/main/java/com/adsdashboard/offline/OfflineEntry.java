package com.adsdashboard.offline;

import java.time.LocalDate;

/**
 * 오프라인 광고비 1건.
 *  - date: 실제 결제 일자
 *  - hq:   본부명 (예: 수도권1본부)
 *  - center: 센터명 (예: 수도권통합)
 *  - service: 서비스 (공통/방문요양/주간보호 등)
 *  - mediaType: 매체 분류 — "오프라인 유입" 우선, 비어있거나 "기타"면 비고에서 키워드 추출
 *  - kind: 시트의 "종류" 컬럼 (오프라인 / 센터지원 / 판촉물 등)
 *  - note: 비고 원문
 *  - amount: 금액 (원). ₩ / , / 공백 제거 후 long.
 */
public record OfflineEntry(
    LocalDate date,
    String hq,
    String center,
    String service,
    String mediaType,
    String kind,
    String note,
    long amount) {}
