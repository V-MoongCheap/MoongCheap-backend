package com.moongcheap_backend.common.metrics;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class MetricsConfiguration {
    @Bean
    public ThreadPoolTaskScheduler metricsTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("backlog-metrics-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    // 업무 스케줄러가 metricsTaskScheduler를 기본 스케줄러로 선택하지 않도록 분리한다.
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }
}
