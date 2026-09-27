package com.moongcheap_backend;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoongCheapBackendApplication {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("Asia/Seoul");

    public static void main(String[] args) {
        // LocalDateTime.now()를 사용하는 기존 도메인 로직까지 모두 KST로 통일한다.
        TimeZone.setDefault(TimeZone.getTimeZone(APPLICATION_ZONE));
        SpringApplication.run(MoongCheapBackendApplication.class, args);
    }

}
