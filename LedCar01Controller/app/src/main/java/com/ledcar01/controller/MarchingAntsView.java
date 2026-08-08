package com.ledcar01.controller;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * Selection border for the zone pill, shaped to a half-pill (rounded outer
 * end, square inner edge) or a full pill using the same per-corner radii
 * format as GradientDrawable.setCornerRadii. Two modes:
 *  - Single zone selected: dashed "marching ants", RGB and DMX running in
 *    opposite directions (see setReversed) so the two read as distinct.
 *  - Both zones selected: a solid stroke that breathes opacity with a soft
 *    glow instead of two dash directions meeting awkwardly at the seam.
 */
public class MarchingAntsView extends View {

    private static final float DASH_ON_DP = 7f;
    private static final float DASH_OFF_DP = 5f;
    private static final long DASH_CYCLE_MS = 700;
    private static final long BREATHE_HALF_CYCLE_MS = 750;
    private static final float BREATHE_MIN_ALPHA = 0.35f;
    /**
     * Extra inset reserved around the path in breathing mode so the glow has
     * room to spread before hitting the view's own (rectangular) bounds -
     * without it, the soft round glow gets hard-clipped at the corners and
     * reads as a faded rectangle instead of a rounded halo.
     */
    private static final float BREATHE_INSET_DP = 8f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float[] radii = new float[8];
    private ValueAnimator animator;
    private boolean reversed = false;
    private boolean breathing = false;
    private float breatheAlpha = 1f;

    public MarchingAntsView(Context context) {
        this(context, null);
    }

    public MarchingAntsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.WHITE);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(7));
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
            // Breathing only ever applies to the full (both-zones) pill, where
            // every corner is already equal - recompute the radius from the
            // inset rect itself so it stays a true stadium shape instead of
            // reusing the outer radius (sized for the un-inset view) which
            // would now be too large for the smaller rect.
            float r = rect.height() / 2f;
            path.addRoundRect(rect, r, r, Path.Direction.CW);
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
        animator = ValueAnimator.ofFloat(1f, BREATHE_MIN_ALPHA);
        animator.setDuration(BREATHE_HALF_CYCLE_MS);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a -> {
            breatheAlpha = (float) a.getAnimatedValue();
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
        if (breathing) {
            // Two passes: a wide blurred halo for the glow, then the crisp
            // line on top - more reliably visible than a shadow layer alone.
            glowPaint.setAlpha(Math.round(breatheAlpha * 220));
            glowPaint.setMaskFilter(new BlurMaskFilter(dp(6) + dp(6) * breatheAlpha, BlurMaskFilter.Blur.NORMAL));
            canvas.drawPath(path, glowPaint);
            paint.setAlpha(Math.round((BREATHE_MIN_ALPHA + (1f - BREATHE_MIN_ALPHA) * breatheAlpha) * 255));
        }
        canvas.drawPath(path, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
