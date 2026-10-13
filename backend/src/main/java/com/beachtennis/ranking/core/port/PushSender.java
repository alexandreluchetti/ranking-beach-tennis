package com.beachtennis.ranking.core.port;

import java.util.Map;

/**
 * Delivers a push message to one device. Delivery also depends on the OS permissions and
 * settings of the device, so it is always best-effort.
 */
public interface PushSender {

    Result send(String deviceToken, String title, String body, Map<String, String> data);

    enum Result {
        SENT,
        /** The token no longer exists (app uninstalled, token rotated): stop using it. */
        INVALID_TOKEN,
        /** Temporary or unknown failure; the in-app notification remains. */
        FAILED,
        /** Push is switched off in this deployment. */
        DISABLED
    }
}
