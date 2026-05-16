package com.adsdashboard.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 기반 배치 작업을 활성화한다.
 *
 * @see com.adsdashboard.common.SheetSyncScheduler 시트 데이터 하루 2회 자동 동기화
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
