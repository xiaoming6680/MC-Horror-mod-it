package com.xm6680.it.analog;

import com.xm6680.it.watching.HorrorPhase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record ReceiverMessage(ReceiverMessageType type, HorrorPhase phase, long receivedEpochSecond, String text) {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public String formattedText() {
        if (type == ReceiverMessageType.SYSTEM_ERROR) {
            return "[错误] " + text;
        }

        String time = Instant.ofEpochSecond(receivedEpochSecond)
                .atZone(ZoneId.systemDefault())
                .format(TIME_FORMATTER);
        return "[" + time + "] " + text;
    }
}
