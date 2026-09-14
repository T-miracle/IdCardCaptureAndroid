package io.github.uniidcardcapture;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.os.Environment;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen, landscape capture UI matching the supplied prototype. It owns preview scaling,
 * mask rendering, JPEG rotation and center-cropping to the statutory ID-card aspect ratio.
 */
public class IdCardCaptureActivity extends Activity implements SurfaceHolder.Callback {
    public static final String EXTRA_SESSION_ID = "sessionId";
    public static final String EXTRA_SIDE = "side";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_ERROR_CODE = "resultErrorCode";
    public static final String EXTRA_RESULT_MESSAGE = "resultMessage";
    public static final String EXTRA_RESULT_SIDE = "resultSide";
    public static final String EXTRA_RESULT_PATH = "resultPath";
    public static final String EXTRA_RESULT_FRONT_PATH = "resultFrontPath";
    public static final String EXTRA_RESULT_BACK_PATH = "resultBackPath";
    private static final int CAMERA_PERMISSION_REQUEST = 2401;
    private static final float ID_CARD_RATIO = CaptureMaskView.ID_CARD_RATIO;
    private static final String CAMERA_LOG_TAG = "IDC-CAMERA";

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private CaptureMaskView maskView;
    private TextView retakeButton;
    private TextView doneButton;
    private ImageView frontPreview;
    private ImageView backPreview;
    private TextView frontStateLabel;
    private TextView backStateLabel;
    private View shutterButton;
    private View sidePanel;
    private Camera camera;
    private int cameraId = -1;
    private int jpegRotation;
    private String sessionId;
    /** The active slot always starts at the front side, regardless of the legacy input option. */
    private String activeSide = "front";
    private String frontPath;
    private String backPath;
    private boolean permissionRequested;
    /** Surface validity and preview state must be tracked separately from the Camera object. */
    private boolean surfaceReady;
    private boolean previewStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        configureSystemBars();
        buildContentView();
        ensureCameraPermission();
    }

    /** Keeps system bars from inheriting the host application's white theme in landscape mode. */
    private void configureSystemBars() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(0x99000000);
        window.setNavigationBarColor(0x99000000);
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    /** Creates the prototype layout without XML resources so the AAR has no host-theme dependency. */
    private void buildContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new AspectRatioSurfaceView(this);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        root.addView(surfaceView, matchParentParams());

        maskView = new CaptureMaskView(this);
        maskView.setActiveSide(activeSide);
        root.addView(maskView, matchParentParams());
        root.addView(createTopBar(), topBarParams());
        shutterButton = createShutterButton();
        root.addView(shutterButton, shutterParams());
        sidePanel = createSidePanel();
        root.addView(sidePanel, sidePanelParams());
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            positionShutterBetweenGuideAndPanel();
        });
        setContentView(root);
        updateCompleteState();
    }

    private FrameLayout.LayoutParams matchParentParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        // AspectRatioSurfaceView can intentionally exceed its parent on one axis. Center it so
        // the FrameLayout crops surplus preview pixels symmetrically instead of stretching them.
        params.gravity = Gravity.CENTER;
        return params;
    }

    private FrameLayout.LayoutParams topBarParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(64),
            Gravity.TOP
        );
        params.leftMargin = dp(24);
        params.rightMargin = dp(24);
        return params;
    }

    private FrameLayout.LayoutParams shutterParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(76), dp(76));
        params.gravity = Gravity.CENTER;
        // Keep a visible separation from the right-side ID-card panel.
        params.leftMargin = dp(84);
        return params;
    }

    private FrameLayout.LayoutParams sidePanelParams() {
        // 168 / 105 keeps the 85.60 : 53.98 ID-card ratio while reducing each slot by one quarter.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(168), dp(226));
        params.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        params.rightMargin = dp(28);
        return params;
    }

    private View createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(createBackButton(), new LinearLayout.LayoutParams(dp(138), dp(48)));

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        retakeButton = textButton("重拍", 0xFFF46A00, Color.WHITE, Color.WHITE);
        retakeButton.setOnClickListener(view -> retake());
        bar.addView(retakeButton, new LinearLayout.LayoutParams(dp(104), dp(44)));
        doneButton = textButton("完成", 0xFF57BE2C, Color.WHITE, Color.WHITE);
        doneButton.setOnClickListener(view -> complete());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(104), dp(44));
        doneParams.leftMargin = dp(12);
        bar.addView(doneButton, doneParams);
        return bar;
    }

    /** Creates an arrow and label with independent text metrics to keep their visual centers aligned. */
    private View createBackButton() {
        LinearLayout button = new LinearLayout(this);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setOnClickListener(view -> cancel());

        button.addView(createBackChevron(), new LinearLayout.LayoutParams(dp(26), dp(40)));

        TextView label = new TextView(this);
        label.setText("返回");
        label.setTextColor(Color.WHITE);
        label.setTextSize(20);
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER_VERTICAL);
        button.addView(label, new LinearLayout.LayoutParams(dp(72), dp(40)));
        return button;
    }

    /** Draws a geometric chevron at its exact view center, avoiding font baseline differences. */
    private View createBackChevron() {
        return new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float centerX = getWidth() / 2f;
                float centerY = getHeight() / 2f;
                float halfHeight = dp(9);
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setStrokeCap(Paint.Cap.SQUARE);
                canvas.drawLine(centerX + dp(4), centerY - halfHeight, centerX - dp(5), centerY, paint);
                canvas.drawLine(centerX - dp(5), centerY, centerX + dp(4), centerY + halfHeight, paint);
            }
        };
    }

    private View createShutterButton() {
        TextView shutter = new TextView(this);
        shutter.setGravity(Gravity.CENTER);
        shutter.setText("拍照");
        shutter.setTextSize(13);
        shutter.setTextColor(Color.DKGRAY);
        shutter.setBackground(roundDrawable(Color.WHITE, Color.DKGRAY, dp(4), dp(38)));
        shutter.setOnClickListener(view -> takePhoto());
        return shutter;
    }

    /** Positions the shutter in the actual gap between the guide frame and the right-side slots. */
    private void positionShutterBetweenGuideAndPanel() {
        if (maskView == null || sidePanel == null || shutterButton == null
            || shutterButton.getWidth() <= 0 || sidePanel.getLeft() <= 0) {
            return;
        }
        float targetCenterX = (maskView.getFrameRect().right + sidePanel.getLeft()) / 2f;
        shutterButton.setX(targetCenterX - shutterButton.getWidth() / 2f);
        shutterButton.setY((maskView.getHeight() - shutterButton.getHeight()) / 2f);
    }

    private View createSidePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(createCardSlot("身份证正面", true, true), new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(105)
        ));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(105)
        );
        backParams.topMargin = dp(16);
        panel.addView(createCardSlot("身份证反面", false, false), backParams);
        return panel;
    }

    /** Creates a 224dp × 140dp slot, preserving the statutory ID-card aspect ratio. */
    private View createCardSlot(String label, boolean active, boolean front) {
        FrameLayout slot = new FrameLayout(this);
        String targetSide = front ? "front" : "back";
        slot.setOnClickListener(view -> selectSide(targetSide));
        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setVisibility(View.GONE);
        slot.addView(preview, matchParentParams());
        if (front) {
            frontPreview = preview;
        } else {
            backPreview = preview;
        }

        TextView stateLabel = captureStateLabel(label, active);
        if (front) {
            frontStateLabel = stateLabel;
        } else {
            backStateLabel = stateLabel;
        }
        slot.addView(stateLabel, matchParentParams());
        return slot;
    }

    private TextView captureStateLabel(String label, boolean active) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(18);
        int color = active ? 0xFF57BE2C : Color.WHITE;
        view.setTextColor(color);
        view.setBackground(dashedDrawable(Color.TRANSPARENT, color, dp(1), 0));
        return view;
    }

    private TextView textButton(String text, int background, int textColor, int strokeColor) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(17);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundDrawable(background, strokeColor, dp(1), dp(4)));
        return button;
    }

    private GradientDrawable roundDrawable(int color, int strokeColor, int strokeWidth, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(strokeWidth, strokeColor);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable dashedDrawable(int color, int strokeColor, int strokeWidth, int radius) {
        GradientDrawable drawable = roundDrawable(color, strokeColor, strokeWidth, radius);
        drawable.setStroke(strokeWidth, strokeColor, dp(5), dp(3));
        return drawable;
    }

    private void ensureCameraPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCameraIfReady();
            return;
        }
        if (!permissionRequested) {
            permissionRequested = true;
            requestPermissions(new String[]{ Manifest.permission.CAMERA }, CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST
            && grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCameraIfReady();
        } else {
            fail("CAMERA_DENIED", "未获得相机权限");
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        Log.i(CAMERA_LOG_TAG, "surfaceCreated valid=" + holder.getSurface().isValid());
        openCameraIfReady();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(CAMERA_LOG_TAG, "surfaceChanged " + width + "x" + height);
        if (camera != null && previewStarted) {
            restartPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        previewStarted = false;
        Log.i(CAMERA_LOG_TAG, "surfaceDestroyed");
        releaseCamera();
    }

    private void openCameraIfReady() {
        if (camera != null || surfaceHolder == null || !surfaceReady
            || !surfaceHolder.getSurface().isValid()
            || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.i(CAMERA_LOG_TAG, "camera deferred: existing=" + (camera != null)
                + ", surfaceReady=" + surfaceReady
                + ", surfaceValid=" + (surfaceHolder != null && surfaceHolder.getSurface().isValid()));
            return;
        }
        cameraId = findBackCamera();
        if (cameraId < 0) {
            fail("CAMERA_UNAVAILABLE", "设备没有可用的后置相机");
            return;
        }
        try {
            Log.i(CAMERA_LOG_TAG, "opening rear camera id=" + cameraId);
            camera = Camera.open(cameraId);
            configureCamera();
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            previewStarted = true;
            Log.i(CAMERA_LOG_TAG, "preview started");
        } catch (IOException | RuntimeException exception) {
            Log.e(CAMERA_LOG_TAG, "preview startup failed", exception);
            releaseCamera();
            fail("CAMERA_OPEN_FAILED", "相机启动失败，请重新进入页面");
        }
    }

    private void configureCamera() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degrees = displayDegrees();
        int displayOrientation = (info.orientation - degrees + 360) % 360;
        jpegRotation = (info.orientation + degrees) % 360;
        camera.setDisplayOrientation(displayOrientation);

        Camera.Parameters parameters = camera.getParameters();
        Camera.Size preview = choosePreviewSize(parameters.getSupportedPreviewSizes());
        if (preview != null) {
            parameters.setPreviewSize(preview.width, preview.height);
            int previewWidth = displayOrientation % 180 == 0 ? preview.width : preview.height;
            int previewHeight = displayOrientation % 180 == 0 ? preview.height : preview.width;
            ((AspectRatioSurfaceView) surfaceView).setAspectRatio(previewWidth, previewHeight);
            Log.i(CAMERA_LOG_TAG, "preview size=" + preview.width + "x" + preview.height
                + ", displayed=" + previewWidth + "x" + previewHeight
                + ", orientation=" + displayOrientation);
        }
        Camera.Size picture = choosePictureSize(parameters.getSupportedPictureSizes());
        if (picture != null) {
            parameters.setPictureSize(picture.width, picture.height);
            Log.i(CAMERA_LOG_TAG, "picture size=" + picture.width + "x" + picture.height
                + ", jpegRotation=" + jpegRotation);
        }
        parameters.setRotation(jpegRotation);
        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        camera.setParameters(parameters);
    }

    private int findBackCamera() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int index = 0; index < Camera.getNumberOfCameras(); index++) {
            Camera.getCameraInfo(index, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return index;
            }
        }
        return -1;
    }

    private int displayDegrees() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_90) {
            return 90;
        }
        if (rotation == Surface.ROTATION_180) {
            return 180;
        }
        if (rotation == Surface.ROTATION_270) {
            return 270;
        }
        return 0;
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Camera.Size best = sizes.get(0);
        float targetRatio = getResources().getDisplayMetrics().widthPixels
            / (float) getResources().getDisplayMetrics().heightPixels;
        float smallestDifference = Float.MAX_VALUE;
        for (Camera.Size size : sizes) {
            float difference = Math.abs((size.width / (float) size.height) - targetRatio);
            if (difference < smallestDifference) {
                smallestDifference = difference;
                best = size;
            }
        }
        return best;
    }

    private Camera.Size choosePictureSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Camera.Size best = sizes.get(0);
        for (Camera.Size size : sizes) {
            if (size.width * size.height > best.width * best.height) {
                best = size;
            }
        }
        return best;
    }

    private void takePhoto() {
        if (camera == null || !previewStarted) {
            Log.w(CAMERA_LOG_TAG, "capture blocked: camera=" + (camera != null)
                + ", previewStarted=" + previewStarted);
            Toast.makeText(this, "相机正在准备，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            previewStarted = false;
            camera.takePicture(null, null, (data, ignoredCamera) -> processCapturedJpeg(data));
        } catch (RuntimeException exception) {
            Log.e(CAMERA_LOG_TAG, "takePicture failed", exception);
            Toast.makeText(this, "拍照失败，请重试", Toast.LENGTH_SHORT).show();
            restartPreview();
        }
    }

    private void processCapturedJpeg(byte[] jpegData) {
        try {
            Bitmap bitmap = decodeCapture(jpegData);
            Bitmap rotated = rotate(bitmap, jpegRotation);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            Bitmap card = cropToGuideFrame(rotated);
            if (card != rotated) {
                rotated.recycle();
            }
            String capturedPath = saveCapture(card, activeSide);
            setPathForActiveSide(capturedPath);
            previewForCurrentSide().setImageBitmap(card);
            previewForCurrentSide().setVisibility(View.VISIBLE);
            // Keep the dashed frame above the thumbnail, but remove its placeholder label.
            updateSlotState();
            releaseCamera();
            if ("front".equals(activeSide) && backPath == null) {
                // The normal workflow continues directly to the reverse side after a front capture.
                selectSide("back");
            }
        } catch (IOException | RuntimeException exception) {
            Log.e(CAMERA_LOG_TAG, "JPEG processing failed", exception);
            Toast.makeText(this, "图片处理失败，请重拍", Toast.LENGTH_SHORT).show();
            restartPreview();
        }
    }

    private Bitmap decodeCapture(byte[] jpegData) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 2400);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, options);
        if (bitmap == null) {
            throw new IllegalStateException("JPEG decode failed");
        }
        return bitmap;
    }

    private int sampleSize(int width, int height, int maxDimension) {
        int sampleSize = 1;
        while (Math.max(width / sampleSize, height / sampleSize) > maxDimension) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private Bitmap rotate(Bitmap source, int rotation) {
        if (rotation == 0) {
            return source;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private Bitmap cropToCardRatio(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int cropWidth = width;
        int cropHeight = Math.round(cropWidth / ID_CARD_RATIO);
        if (cropHeight > height) {
            cropHeight = height;
            cropWidth = Math.round(cropHeight * ID_CARD_RATIO);
        }
        int left = Math.max(0, (width - cropWidth) / 2);
        int top = Math.max(0, (height - cropHeight) / 2);
        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight);
    }

    /**
     * Maps the on-screen white guide rectangle back to the JPEG. The preview is center-cropped,
     * so a 4:3 picture and a 16:9 preview can have different dimensions while still describing
     * the same camera center. When the UI geometry is unavailable, it falls back to a centered
     * ID-card-ratio crop.
     */
    private Bitmap cropToGuideFrame(Bitmap source) {
        RectF guide = maskView == null ? null : maskView.getFrameRect();
        if (guide == null || guide.isEmpty() || surfaceView == null
            || surfaceView.getWidth() <= 0 || surfaceView.getHeight() <= 0) {
            return cropToCardRatio(source);
        }

        float previewLeft = surfaceView.getLeft();
        float previewTop = surfaceView.getTop();
        float previewWidth = surfaceView.getWidth();
        float previewHeight = surfaceView.getHeight();
        float normalizedLeft = clamp((guide.left - previewLeft) / previewWidth, 0f, 1f);
        float normalizedTop = clamp((guide.top - previewTop) / previewHeight, 0f, 1f);
        float normalizedRight = clamp((guide.right - previewLeft) / previewWidth, 0f, 1f);
        float normalizedBottom = clamp((guide.bottom - previewTop) / previewHeight, 0f, 1f);
        if (normalizedRight <= normalizedLeft || normalizedBottom <= normalizedTop) {
            return cropToCardRatio(source);
        }

        float sourceWidth = source.getWidth();
        float sourceHeight = source.getHeight();
        float sourceRatio = sourceWidth / sourceHeight;
        float previewRatio = previewWidth / previewHeight;
        float visibleLeft = 0f;
        float visibleTop = 0f;
        float visibleWidth = sourceWidth;
        float visibleHeight = sourceHeight;
        // Derive the part of the JPEG that corresponds to the center-cropped preview stream.
        if (sourceRatio > previewRatio) {
            visibleWidth = sourceHeight * previewRatio;
            visibleLeft = (sourceWidth - visibleWidth) / 2f;
        } else if (sourceRatio < previewRatio) {
            visibleHeight = sourceWidth / previewRatio;
            visibleTop = (sourceHeight - visibleHeight) / 2f;
        }

        int left = Math.max(0, Math.round(visibleLeft + normalizedLeft * visibleWidth));
        int top = Math.max(0, Math.round(visibleTop + normalizedTop * visibleHeight));
        int right = Math.min(source.getWidth(), Math.round(visibleLeft + normalizedRight * visibleWidth));
        int bottom = Math.min(source.getHeight(), Math.round(visibleTop + normalizedBottom * visibleHeight));
        if (right <= left || bottom <= top) {
            return cropToCardRatio(source);
        }
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private String saveCapture(Bitmap bitmap, String captureSide) throws IOException {
        File pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (pictures == null && !getFilesDir().exists()) {
            throw new IOException("app storage unavailable");
        }
        File outputDirectory = pictures == null ? getFilesDir() : pictures;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        File output = new File(outputDirectory, "idcard_" + captureSide + "_" + timestamp + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                throw new IOException("JPEG write failed");
            }
        }
        return output.getAbsolutePath();
    }

    private void retake() {
        setPathForActiveSide(null);
        previewForCurrentSide().setImageDrawable(null);
        previewForCurrentSide().setVisibility(View.GONE);
        updateSlotState();
        openCameraIfReady();
    }

    /** Returns the visible right-panel slot associated with the capture mode opened by UniApp. */
    private ImageView previewForCurrentSide() {
        return "back".equals(activeSide) ? backPreview : frontPreview;
    }

    /** Selects a capture slot; an empty slot immediately resumes the camera for that side. */
    private void selectSide(String targetSide) {
        activeSide = "back".equals(targetSide) ? "back" : "front";
        if (maskView != null) {
            maskView.setActiveSide(activeSide);
        }
        updateSlotState();
        if (pathForActiveSide() == null) {
            openCameraIfReady();
        } else {
            releaseCamera();
        }
    }

    /** Applies green selection borders while preserving a thumbnail instead of a placeholder label. */
    private void updateSlotState() {
        updateSlotLabel(frontStateLabel, "身份证正面", "front".equals(activeSide), frontPath != null);
        updateSlotLabel(backStateLabel, "身份证反面", "back".equals(activeSide), backPath != null);
        updateCompleteState();
    }

    private void updateSlotLabel(TextView label, String placeholder, boolean selected, boolean hasCapture) {
        if (label == null) {
            return;
        }
        int color = selected ? 0xFF57BE2C : Color.WHITE;
        label.setText(hasCapture ? "" : placeholder);
        label.setTextColor(color);
        label.setBackground(dashedDrawable(Color.TRANSPARENT, color, dp(1), 0));
    }

    private String pathForActiveSide() {
        return "back".equals(activeSide) ? backPath : frontPath;
    }

    private void setPathForActiveSide(String path) {
        if ("back".equals(activeSide)) {
            backPath = path;
        } else {
            frontPath = path;
        }
    }

    /** Enables completion only when both sides have a captured local file. */
    private void updateCompleteState() {
        if (doneButton == null) {
            return;
        }
        boolean canComplete = frontPath != null && backPath != null;
        doneButton.setEnabled(canComplete);
        doneButton.setAlpha(canComplete ? 1f : 0.45f);
    }

    private void complete() {
        if (frontPath == null || backPath == null) {
            return;
        }
        JSONObject result = new JSONObject();
        result.put("code", 0);
        result.put("side", activeSide);
        // path and uri remain for older callers; new callers should consume both side-specific paths.
        result.put("path", pathForActiveSide());
        result.put("uri", Uri.fromFile(new File(pathForActiveSide())).toString());
        result.put("frontPath", frontPath);
        result.put("backPath", backPath);
        result.put("images", createImageResultList());
        finishWithResult(RESULT_OK, result);
    }

    /** Creates the ordered front/back result list consumed by the UniApp caller. */
    private JSONArray createImageResultList() {
        JSONArray images = new JSONArray();
        images.add(createImageResult("front", frontPath));
        images.add(createImageResult("back", backPath));
        return images;
    }

    private JSONObject createImageResult(String imageSide, String path) {
        JSONObject image = new JSONObject();
        image.put("side", imageSide);
        image.put("path", path);
        image.put("uri", Uri.fromFile(new File(path)).toString());
        return image;
    }

    private void cancel() {
        JSONObject result = new JSONObject();
        result.put("code", 1);
        result.put("errorCode", "CANCELLED");
        result.put("message", "已取消证件采集");
        finishWithResult(RESULT_CANCELED, result);
    }

    private void fail(String errorCode, String message) {
        JSONObject result = new JSONObject();
        result.put("code", -1);
        result.put("errorCode", errorCode);
        result.put("message", message);
        finishWithResult(RESULT_FIRST_USER, result);
    }

    /** Sends a normal Activity result for the demo app and the UniApp callback for production. */
    private void finishWithResult(int resultCode, JSONObject result) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_RESULT_CODE, result.getIntValue("code"));
        resultIntent.putExtra(EXTRA_RESULT_ERROR_CODE, result.getString("errorCode"));
        resultIntent.putExtra(EXTRA_RESULT_MESSAGE, result.getString("message"));
        resultIntent.putExtra(EXTRA_RESULT_SIDE, result.getString("side"));
        resultIntent.putExtra(EXTRA_RESULT_PATH, result.getString("path"));
        resultIntent.putExtra(EXTRA_RESULT_FRONT_PATH, result.getString("frontPath"));
        resultIntent.putExtra(EXTRA_RESULT_BACK_PATH, result.getString("backPath"));
        setResult(resultCode, resultIntent);
        CaptureCallbackRegistry.resolve(sessionId, result);
        finish();
    }

    private void restartPreview() {
        if (camera == null) {
            openCameraIfReady();
            return;
        }
        try {
            camera.startPreview();
            previewStarted = true;
            Log.i(CAMERA_LOG_TAG, "preview restarted");
        } catch (RuntimeException exception) {
            Log.e(CAMERA_LOG_TAG, "preview restart failed", exception);
            retake();
        }
    }

    private void releaseCamera() {
        if (camera == null) {
            return;
        }
        try {
            camera.stopPreview();
        } catch (RuntimeException ignored) {
            // The preview can already be stopped after takePicture().
        }
        camera.release();
        camera = null;
        previewStarted = false;
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    @Override
    protected void onDestroy() {
        releaseCamera();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
