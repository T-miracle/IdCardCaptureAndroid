package io.github.uniidcardcapture;

import android.content.Context;
import android.content.Intent;

import com.alibaba.fastjson.JSONObject;
import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;

import java.util.UUID;

/**
 * UniApp Module entry point. The module only starts the native screen; camera handling and
 * bitmap processing belong to {@link IdCardCaptureActivity} so the Vue layer has no camera state.
 */
public class IdCardCaptureModule extends UniModule {
    public static final String MODULE_NAME = "uni-id-card-capture";

    /**
     * Opens the landscape ID-card capture screen.
     *
     * @param options accepts {@code side}, either {@code front} or {@code back}
     * @param callback receives one object with {@code code}, {@code path}, {@code uri}, and {@code side}
     */
    @UniJSMethod(uiThread = true)
    public void capture(JSONObject options, UniJSCallback callback) {
        if (callback == null) {
            return;
        }
        Context context = mUniSDKInstance == null ? null : mUniSDKInstance.getContext();
        if (context == null) {
            callback.invoke(error("CONTEXT_UNAVAILABLE", "无法启动证件采集页面"));
            return;
        }

        String side = options == null ? "front" : options.getString("side");
        if (!"back".equals(side)) {
            side = "front";
        }
        String sessionId = UUID.randomUUID().toString();
        CaptureCallbackRegistry.register(sessionId, result -> callback.invoke(result));

        Intent intent = new Intent(context, IdCardCaptureActivity.class);
        intent.putExtra(IdCardCaptureActivity.EXTRA_SESSION_ID, sessionId);
        intent.putExtra(IdCardCaptureActivity.EXTRA_SIDE, side);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (RuntimeException exception) {
            CaptureCallbackRegistry.resolve(sessionId, error("OPEN_FAILED", "证件采集页面启动失败"));
        }
    }

    private JSONObject error(String errorCode, String message) {
        JSONObject result = new JSONObject();
        result.put("code", -1);
        result.put("errorCode", errorCode);
        result.put("message", message);
        return result;
    }
}
