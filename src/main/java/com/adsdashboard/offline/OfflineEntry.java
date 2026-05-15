package com.adsdashboard.offline;

import java.time.LocalDate;

/**
 * 오프라인 광고비 1건.
 *  - date: 실제 결제 일자
 *  - hq:   본부명 (예: 수도권1본부)
 *  - center: 센터명 (예: 수도권통합)
 *  - service: 서비스 (공통/방문요양/주간보호 등) — 시트의 "서비스" 컬럼 값
 *  - mediaType: "오프라인 유입" 컬럼 값 (예: 신문광고, 전단지, OOH, 라디오 등)
 *  - amount: 금액 (원). ₩ / , / 공백 제거 후 long.
 */
public record OfflineEntry(
    LocalDate date,
    String hq,
    String center,
    String service,
    String mediaType,
    long amount) {}
