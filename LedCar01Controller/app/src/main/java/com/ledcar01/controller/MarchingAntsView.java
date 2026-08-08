package com.ledcar01.controller;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Selection border for the zone pill, shaped to a half-pill (rounded outer
 * end, square inner edge) or a full pill using the same per-corner radii
 * format as GradientDrawable.setCornerRadii. Two modes:
 *  - Single zone selected: dashed "marching ants", RGB and DMX running in
 *    opposite directions (see setReversed) so the two read as distinct.
 *  - Both zones selected: a full-color rainbow ring continuously rotating
 *    around the pill, with a soft matching-color glow - reads as "every
 *    color, both zones" instead of two dash directions meeting awkwardly
 *    at the seam.
 */
public class MarchingAntsView extends View {

    private static final float DASH_ON_DP = 7f;
    private static final float DASH_OFF_DP = 5f;
    private static final float DASH_STROKE_DP = 2f;
    private static final long DASH_CYCLE_MS = 700;
    private static final long RAINBOW_CYCLE_MS = 2200;
    private static final float RAINBOW_STROKE_DP = 10f;
    private static final float RAINBOW_GLOW_STROKE_DP = 16f;
    private static final int[] RAINBOW_COLORS = {
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED
    };
    /**
     * Extra inset reserved around the path in rainbow mode so the (now much
     * thicker) stroke and its glow have room to spread before hitting the
     * view's own (rectangular) bounds - without it, the soft round glow gets
     * hard-clipped at the corners and reads as a faded rectangle instead of
     * a rounded halo. Kept small so the ring still reads as filling the
     * whole pill rather than floating well inside it.
     */
    private static final float BREATHE_INSET_DP = 3f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix shaderMatrix = new Matrix();
    private float[] radii = new float[8];
    private ValueAnimator animator;
    private boolean reversed = false;
    private boolean breathing = false;
    private SweepGradient rainbowShader;
    private float rainbowAngle = 0f;
    private float pivotX;
    private float pivotY;

    public MarchingAntsView(Context context) {
        this(context, null);
    }

    public MarchingAntsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(DASH_STROKE_DP));
        paint.setColor(Color.WHITE);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(RAINBOW_GLOW_STROKE_DP));
        glowPaint.setColor(Color.WHITE);
        setWillNotDraw(false);
    }

    /** DMX runs the marching ants the opposite way around the pill from RGB, so the two read as distinct. */
    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    /** Both zones active: swap the dashed marching border for a breathing glow. */
    public void setBreathingMode(boolean breathing) {
        if (this.breathing == breathing) {
            return;
        }
        this.breathing = breathing;
        setLayerType(breathing ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_NONE, null);
        rebuildPath();
        invalidate();
        if (isAttachedToWindow()) {
            restartAnimation();
        }
    }

    public void setCornerRadii(float[] radii) {
        this.radii = radii;
        rebuildPath();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildPath();
    }

    private void rebuildPath() {
        path.reset();
        float inset = dp(breathing ? BREATHE_INSET_DP : 1f);
        RectF rect = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        if (breathing) {
            // Rainbow mode only ever applies to the full (both-zones) pill,
            // where every corner is already equal - recompute the radius from
            // the inset rect itself so it stays a true stadium shape instead
            // of reusing the outer radius (sized for the un-inset view) which
            // would now be too large for the smaller rect.
            float r = rect.height() / 2f;
            path.addRoundRect(rect, r, r, Path.Direction.CW);
            pivotX = rect.centerX();
            pivotY = rect.centerY();
            rainbowShader = new SweepGradient(pivotX, pivotY, RAINBOW_COLORS, null);
        } else {
            path.addRoundRect(rect, radii, Path.Direction.CW);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restartAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimating();
    }

    private void restartAnimation() {
        stopAnimating();
        if (breathing) {
            startBreathing();
        } else {
            startDashing();
        }
    }

    private void startDashing() {
        paint.setAlpha(255);
        paint.clearShadowLayer();
        paint.setShader(null); // clear any rainbow shader left over from breathing mode
        paint.setStrokeWidth(dp(DASH_STROKE_DP));
        float dashOn = dp(DASH_ON_DP);
        float dashOff = dp(DASH_OFF_DP);
        animator = ValueAnimator.ofFloat(0f, dashOn + dashOff);
        animator.setDuration(DASH_CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float phase = (float) a.getAnimatedValue();
            paint.setPathEffect(new DashPathEffect(new float[]{dashOn, dashOff}, reversed ? -phase : phase));
            invalidate();
        });
        animator.start();
    }

    private void startBreathing() {
        paint.setPathEffect(null);
        paint.setAlpha(255);
        paint.setStrokeWidth(dp(RAINBOW_STROKE_DP));
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(RAINBOW_CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            rainbowAngle = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimating() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (breathing && rainbowShader != null) {
            shaderMatrix.setRotate(rainbowAngle, pivotX, pivotY);
            rainbowShader.setLocalMatrix(shaderMatrix);
            // Two passes: a wide blurred halo colored by the same rotating
            // rainbow shader for the glow, then the crisp ring on top - more
            // reliably visible than a shadow layer alone.
            glowPaint.setShader(rainbowShader);
            glowPaint.setAlpha(200);
            glowPaint.setMaskFilter(new BlurMaskFilter(dp(14), BlurMaskFilter.Blur.NORMAL));
            canvas.drawPath(path, glowPaint);
            paint.setShader(rainbowShader);
        }
        canvas.drawPath(path, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
