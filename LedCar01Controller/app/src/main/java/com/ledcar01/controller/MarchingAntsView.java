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
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Selection border for the zone pill, shaped to a half-pill (rounded outer
 * end, square inner edge) or a full pill using the same per-corner radii
 * format as GradientDrawable.setCornerRadii. Two modes:
 *  - Single zone selected: dashed "marching ants", RGB and DMX running in
 *    opposite directions (see setReversed) so the two read as distinct.
 *  - Both zones selected: a steady ring in the blended RGB/DMX color with an
 *    occasional random flicker, like a slightly flaky neon tube - reads as
 *    "alive" without spinning or sweeping.
 */
public class MarchingAntsView extends View {

    private static final float DASH_ON_DP = 7f;
    private static final float DASH_OFF_DP = 5f;
    private static final float DASH_STROKE_DP = 2f;
    private static final long DASH_CYCLE_MS = 700;
    private static final float NEON_STROKE_DP = 5f;
    private static final float NEON_GLOW_STROKE_DP = 14f;
    /** How many flicker "buckets" per second - each bucket independently rolls whether to dim. */
    private static final float FLICKER_RATE_HZ = 14f;
    /** Fraction of buckets that flicker; higher = flakier tube. */
    private static final float FLICKER_CHANCE = 0.88f;
    /**
     * Extra inset reserved around the path in "both zones" mode so the
     * stroke and its glow have room to spread before hitting the view's own
     * (rectangular) bounds - without it, the soft round glow gets
     * hard-clipped at the corners and reads as a faded rectangle instead of
     * a rounded halo.
     */
    private static final float BOTH_INSET_DP = 3f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float[] radii = new float[8];
    private ValueAnimator animator;
    private boolean reversed = false;
    private boolean breathing = false;
    private int rgbColor = Color.RED;
    private int dmxColor = Color.BLUE;
    private int neonColor = Color.WHITE;
    private long neonStartTime = 0L;

    public MarchingAntsView(Context context) {
        this(context, null);
    }

    public MarchingAntsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(DASH_STROKE_DP));
        paint.setColor(Color.WHITE);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(NEON_GLOW_STROKE_DP));
        glowPaint.setColor(Color.WHITE);
        setWillNotDraw(false);
    }

    /** DMX runs the marching ants the opposite way around the pill from RGB, so the two read as distinct. */
    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    /** Both zones active: swap the dashed marching border for the neon-flicker effect. */
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

    /** Live per-zone colors, blended 50/50 for the neon tube's steady color. */
    public void setZoneColors(int rgbColor, int dmxColor) {
        this.rgbColor = rgbColor;
        this.dmxColor = dmxColor;
        neonColor = Color.rgb(
                (Color.red(rgbColor) + Color.red(dmxColor)) / 2,
                (Color.green(rgbColor) + Color.green(dmxColor)) / 2,
                (Color.blue(rgbColor) + Color.blue(dmxColor)) / 2);
        if (breathing) {
            invalidate();
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
        float inset = dp(breathing ? BOTH_INSET_DP : 1f);
        RectF rect = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        if (breathing) {
            // The neon ring only ever applies to the full (both-zones) pill,
            // where every corner is already equal - recompute the radius
            // from the inset rect itself so it stays a true stadium shape
            // instead of reusing the outer radius (sized for the un-inset
            // view) which would now be too large for the smaller rect.
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
            startFlickering();
        } else {
            startDashing();
        }
    }

    private void startDashing() {
        paint.setAlpha(255);
        paint.clearShadowLayer();
        paint.setShader(null);
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

    private void startFlickering() {
        paint.setPathEffect(null);
        paint.setStrokeWidth(dp(NEON_STROKE_DP));
        neonStartTime = SystemClock.uptimeMillis();
        // The animated value itself isn't used - this just keeps the view
        // invalidating at display-frame rate so the time-bucketed flicker
        // (see onDraw) is sampled often enough to read as flickering rather
        // than stepping.
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> invalidate());
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
            float elapsedSec = (SystemClock.uptimeMillis() - neonStartTime) / 1000f;
            long bucket = (long) (elapsedSec * FLICKER_RATE_HZ);
            float roll = pseudoRandom(bucket);
            float flicker = roll > FLICKER_CHANCE ? 0.25f + pseudoRandom(bucket + 99) * 0.3f : 1f;

            glowPaint.setColor(neonColor);
            glowPaint.setShader(null);
            glowPaint.setAlpha(Math.round(150 * flicker));
            glowPaint.setMaskFilter(new BlurMaskFilter(dp(9), BlurMaskFilter.Blur.NORMAL));
            canvas.drawPath(path, glowPaint);

            paint.setColor(neonColor);
            paint.setShader(null);
            paint.setAlpha(Math.round(255 * flicker));
        }
        canvas.drawPath(path, paint);
    }

    /** Deterministic pseudo-random in [0,1) from an integer seed - same hash technique as the HTML prototype. */
    private static float pseudoRandom(long seed) {
        double x = Math.sin(seed * 12.9898) * 43758.5453;
        return (float) (x - Math.floor(x));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
