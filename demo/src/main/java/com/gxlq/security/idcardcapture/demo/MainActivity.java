package io.github.uniidcardcapture.demo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.uniidcardcapture.IdCardCaptureActivity;

/**
 * Standalone host used only for Camera UI debugging. It exercises the same Activity and AAR
 * source as UniApp, while making Android Studio breakpoints and Logcat immediately available.
 */
public final class MainActivity extends Activity {
    private static final int CAPTURE_REQUEST_CODE = 7001;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText("身份证采集原生调试");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        root.addView(title, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView hint = new TextView(this);
        hint.setText("直接打开插件采集页；Android Studio 可在 IDC-CAMERA 日志和断点中调试。");
        hint.setTextSize(15);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        hintParams.topMargin = dp(16);
        root.addView(hint, hintParams);

        root.addView(createCaptureButton("采集身份证正面", "front"), buttonParams());
        root.addView(createCaptureButton("采集身份证反面", "back"), buttonParams());

        resultView = new TextView(this);
        resultView.setText("尚未采集");
        resultView.setTextColor(Color.DKGRAY);
        resultView.setTextIsSelectable(true);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        );
        resultParams.topMargin = dp(20);
        root.addView(resultView, resultParams);
        return root;
    }

    private Button createCaptureButton(String label, String side) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> startCapture(side));
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        );
        params.topMargin = dp(12);
        return params;
    }

    private void startCapture(String side) {
        Intent intent = new Intent(this, IdCardCaptureActivity.class);
        intent.putExtra(IdCardCaptureActivity.EXTRA_SESSION_ID, "android-studio-demo");
        intent.putExtra(IdCardCaptureActivity.EXTRA_SIDE, side);
        startActivityForResult(intent, CAPTURE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CAPTURE_REQUEST_CODE) {
            return;
        }
        if (data == null) {
            resultView.setText("采集页未返回结果，resultCode=" + resultCode);
            return;
        }
        resultView.setText(
            "resultCode=" + resultCode
                + "\n业务状态=" + data.getIntExtra(IdCardCaptureActivity.EXTRA_RESULT_CODE, -999)
                + "\n证件面=" + data.getStringExtra(IdCardCaptureActivity.EXTRA_RESULT_SIDE)
                + "\n照片路径=" + data.getStringExtra(IdCardCaptureActivity.EXTRA_RESULT_PATH)
                + "\n错误=" + data.getStringExtra(IdCardCaptureActivity.EXTRA_RESULT_ERROR_CODE)
                + "\n说明=" + data.getStringExtra(IdCardCaptureActivity.EXTRA_RESULT_MESSAGE)
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
