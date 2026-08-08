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
 *  - Both zones selected: two short bands, one in the RGB color and one in
 *    the DMX color, chasing each other continuously around the pill with a
 *    fixed gap between them.
 */
public class MarchingAntsView extends View {

    private static final float DASH_ON_DP = 7f;
    private static final float DASH_OFF_DP = 5f;
    private static final float DASH_STROKE_DP = 2f;
    private static final long DASH_CYCLE_MS = 700;
    private static final long CHASE_CYCLE_MS = 2400;
    private static final float CHASE_STROKE_DP = 5f;
    private static final float CHASE_GLOW_STROKE_DP = 14f;
    /** Angular gap kept between the two bands, in degrees. */
    private static final float CHASE_GAP_DEG = 90f;
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
    private final Matrix shaderMatrix = new Matrix();
    private float[] radii = new float[8];
    private ValueAnimator animator;
    private boolean reversed = false;
    private boolean breathing = false;
    private int rgbColor = Color.RED;
    private int dmxColor = Color.BLUE;
    private SweepGradient rgbBandShader;
    private SweepGradient dmxBandShader;
    private float chaseProgress = 0f; // 0..1, loops continuously
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
        glowPaint.setStrokeWidth(dp(CHASE_GLOW_STROKE_DP));
        glowPaint.setColor(Color.WHITE);
        setWillNotDraw(false);
    }

    /** DMX runs the marching ants the opposite way around the pill from RGB, so the two read as distinct. */
    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    /** Both zones active: swap the dashed marching border for the chasing-bands effect. */
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

    /** Live per-zone colors, one band per zone. */
    public void setZoneColors(int rgbColor, int dmxColor) {
        this.rgbColor = rgbColor;
        this.dmxColor = dmxColor;
        if (breathing) {
            rebuildBandShaders();
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
            // Chasing bands only ever apply to the full (both-zones) pill,
            // where every corner is already equal - recompute the radius
            // from the inset rect itself so it stays a true stadium shape
            // instead of reusing the outer radius (sized for the un-inset
            // view) which would now be too large for the smaller rect.
            float r = rect.height() / 2f;
            path.addRoundRect(rect, r, r, Path.Direction.CW);
            pivotX = rect.centerX();
            pivotY = rect.centerY();
            rebuildBandShaders();
        } else {
            path.addRoundRect(rect, radii, Path.Direction.CW);
        }
    }

    private void rebuildBandShaders() {
        int[] colors = {Color.TRANSPARENT, rgbColor, Color.TRANSPARENT, Color.TRANSPARENT};
        float[] positions = {0f, 0.10f, 0.22f, 1f};
        rgbBandShader = new SweepGradient(pivotX, pivotY, colors, positions);
        int[] colors2 = {Color.TRANSPARENT, dmxColor, Color.TRANSPARENT, Color.TRANSPARENT};
        dmxBandShader = new SweepGradient(pivotX, pivotY, colors2, positions);
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
            startChasing();
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

    private void startChasing() {
        paint.setPathEffect(null);
        paint.setAlpha(255);
        paint.setStrokeWidth(dp(CHASE_STROKE_DP));
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CHASE_CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            chaseProgress = (float) a.getAnimatedValue();
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
        if (breathing && rgbBandShader != null) {
            float angle = chaseProgress * 360f;
            drawBand(canvas, rgbBandShader, angle);
            drawBand(canvas, dmxBandShader, angle + CHASE_GAP_DEG);
        } else {
            canvas.drawPath(path, paint);
        }
    }

    private void drawBand(Canvas canvas, SweepGradient shader, float angle) {
        shaderMatrix.setRotate(angle, pivotX, pivotY);
        shader.setLocalMatrix(shaderMatrix);

        glowPaint.setShader(shader);
        glowPaint.setAlpha(190);
        glowPaint.setMaskFilter(new BlurMaskFilter(dp(9), BlurMaskFilter.Blur.NORMAL));
        canvas.drawPath(path, glowPaint);

        paint.setShader(shader);
        canvas.drawPath(path, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
