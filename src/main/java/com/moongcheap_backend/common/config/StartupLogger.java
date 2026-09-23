package com.moongcheap_backend.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final BuildProperties buildProperties;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Application started | version={} buildTime={}",
                buildProperties.getVersion(),
                buildProperties.getTime());
    }
}
