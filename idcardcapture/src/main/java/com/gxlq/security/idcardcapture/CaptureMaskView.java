package io.github.uniidcardcapture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Draws the dimmed area and a non-rounded ID-card guide frame above the native camera preview. */
final class CaptureMaskView extends View {
    static final float ID_CARD_RATIO = 85.60f / 53.98f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frameRect = new RectF();

    CaptureMaskView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    RectF getFrameRect() {
        return new RectF(frameRect);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float horizontalPadding = dp(44);
        float panelReserve = Math.min(dp(300), width * 0.28f);
        float maxFrameWidth = Math.max(dp(320), width - horizontalPadding * 2 - panelReserve);
        float maxFrameHeight = height - dp(150);
        float frameWidth = Math.min(maxFrameWidth, maxFrameHeight * ID_CARD_RATIO);
        float frameHeight = frameWidth / ID_CARD_RATIO;
        float left = horizontalPadding;
        float top = (height - frameHeight) / 2f;
        frameRect.set(left, top, left + frameWidth, top + frameHeight);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x99000000);
        canvas.drawRect(0, 0, width, frameRect.top, paint);
        canvas.drawRect(0, frameRect.bottom, width, height, paint);
        canvas.drawRect(0, frameRect.top, frameRect.left, frameRect.bottom, paint);
        canvas.drawRect(frameRect.right, frameRect.top, width, frameRect.bottom, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.WHITE);
        canvas.drawRect(frameRect, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
