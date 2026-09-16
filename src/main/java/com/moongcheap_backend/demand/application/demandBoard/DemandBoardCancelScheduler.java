package com.moongcheap_backend.demand.application.demandBoard;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemandBoardCancelScheduler {

    private static final int CHUNK_SIZE = 1000;
    private static final int MAX_CHUNKS = 100;
    private static final long MAX_RUNTIME_MS = 300_000;
    private static final ZoneId ZONE_SEOUL = ZoneId.of("Asia/Seoul");

    private final DemandBoardCancelChunkService chunkService;

    @Scheduled(cron = "${moongcheap.batch.demand-board-cancel.cron:0 5 * * * *}", zone = "Asia/Seoul")
    public void cancelOverdueGatheringBoards() {
        LocalDateTime threshold = LocalDateTime.now(ZONE_SEOUL);
        long start = System.currentTimeMillis();
        int total = 0;
        int chunks = 0;

        while (chunks < MAX_CHUNKS
            && System.currentTimeMillis() - start < MAX_RUNTIME_MS) {
            Optional<Integer> result = chunkService.cancelChunk(threshold, CHUNK_SIZE);

            if (result.isEmpty()) {
                log.info("Demand board cancel batch preempted: lock held elsewhere, total={}",
                    total);
                return;
            }
            int updated = result.get();
            total += updated;
            chunks++;
            if (updated == 0 || updated < CHUNK_SIZE) {
                break;
            }
        }

        log.info("Demand board cancel batch finished: processed={}, chunks={}, elapsedMs={}",
            total, chunks, System.currentTimeMillis() - start);
    }
}
