package com.lucchetta.dataprovider.push;

import com.lucchetta.core.port.PushSender;

import java.util.Map;

/** Used while push is switched off ({@code app.push.fcm.enabled=false}); in-app notifications still work. */
public class DisabledPushSender implements PushSender {

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        return Result.DISABLED;
    }
}
