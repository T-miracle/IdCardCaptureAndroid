package io.github.uniidcardcapture;

import com.alibaba.fastjson.JSONObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the one-shot UniApp callback alive while the capture Activity is foregrounded.
 * The registry is process-local: a process recreation is reported as a cancelled capture.
 */
final class CaptureCallbackRegistry {
    private static final Map<String, CaptureResultCallback> CALLBACKS = new ConcurrentHashMap<>();

    private CaptureCallbackRegistry() {
    }

    static void register(String sessionId, CaptureResultCallback callback) {
        CALLBACKS.put(sessionId, callback);
    }

    static void resolve(String sessionId, JSONObject result) {
        CaptureResultCallback callback = CALLBACKS.remove(sessionId);
        if (callback != null) {
            callback.onResult(result);
        }
    }

    /** Decouples the capture Activity from the UniApp runtime for standalone Android debugging. */
    interface CaptureResultCallback {
        void onResult(JSONObject result);
    }
}
