package com.moongcheap_backend.common.util;

import java.time.LocalDateTime;

public final class TimeUtils {

    private TimeUtils() {}

    public static LocalDateTime ceilToFiveMinuteMark(LocalDateTime time, boolean bypass) {
        if (bypass) {
            return time;
        }
        LocalDateTime target = time.withMinute(5).withSecond(0).withNano(0);
        if (time.getMinute() > 5) {
            target = target.plusHours(1);
        }
        return target;
    }
}
