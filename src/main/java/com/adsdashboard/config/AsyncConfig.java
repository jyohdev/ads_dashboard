package com.adsdashboard.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 외부 API 를 부채꼴(fan-out)로 동시 호출하기 위한 I/O 전용 스레드풀.
 *
 * <p><b>왜 필요한가</b> — {@code CompletableFuture.supplyAsync(task)} 나
 * {@code parallelStream()} 을 executor 없이 쓰면 JVM 공용 ForkJoinPool 을 쓴다.
 * 그 풀의 병렬도는 {@code availableProcessors() - 1} 이라, vCPU 가 1개인 작은
 * 인스턴스(예: Render)에서는 사실상 1 — "병렬" 호출이 실제로는 직렬로 실행된다.
 *
 * <p>네이버 by-category 조회는 7개 계정 × 캠페인 수만큼 HTTP 호출이 퍼지는데,
 * 이 호출들은 CPU 가 아니라 네트워크 대기가 대부분이다. 그래서 코어 수와 무관하게
 * 넉넉한 고정 폭(32) 풀을 따로 두고, 대기 중인 호출들이 서로를 막지 않게 한다.
 */
@Configuration
public class AsyncConfig {

  /** I/O 바운드 fan-out 호출 전용 — {@code @Qualifier("ioExecutor")} 로 주입. */
  @Bean(name = "ioExecutor")
  public Executor ioExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(32);
    executor.setMaxPoolSize(32);
    executor.setQueueCapacity(1000);
    executor.setKeepAliveSeconds(60);
    executor.setAllowCoreThreadTimeOut(true);   // 유휴 시 스레드 회수 — 평소엔 메모리 점유 0
    executor.setThreadNamePrefix("io-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(20);
    executor.initialize();
    return executor;
  }
}
