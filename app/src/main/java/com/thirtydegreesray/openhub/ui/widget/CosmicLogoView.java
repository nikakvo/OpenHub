package com.thirtydegreesray.openhub.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Animated version of the OpenHub logo for the drawer header: the three-node branch
 * glyph glowing in shifting colors, a tilted orbit with three comets circling it,
 * a small moon around each node and twinkling stars behind.
 *
 * It only animates while it is actually on screen (the drawer is open) and draws a
 * single still frame when the system's animations are turned off.
 */
public class CosmicLogoView extends View {

    /** Logo geometry in the 108-unit space of the launcher icon. */
    private static final float[][] NODES = {{44f, 36f}, {44f, 72f}, {66f, 40f}};
    private static final float GLYPH_CX = 55f;
    private static final float GLYPH_CY = 54f;
    private static final float NODE_R = 6.5f;
    /** How many logo units fit across the view (leaves room for the orbit). */
    private static final float UNITS_ACROSS = 96f;

    private static final int[] NODE_COLORS = {0xFF5EEAD4, 0xFFF472B6, 0xFFFBBF24};
    private static final int[] COMET_COLORS = {0xFF67E8F9, 0xFFC084FC, 0xFFFB923C};
    private static final int STAR_COUNT = 18;
    private static final int TRAIL_STEPS = 14;

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path branchPath = new Path();
    private final float[] stars = new float[STAR_COUNT * 3];
    private final float[] hsv = new float[3];

    @Nullable private BlurMaskFilter blur;
    private float unit;
    private float cx;
    private float cy;
    private long startTime = SystemClock.uptimeMillis();
    private boolean running;

    public CosmicLogoView(@NonNull Context context) {
        this(context, null);
    }

    public CosmicLogoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CosmicLogoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        orbitPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.FILL);
        Random random = new Random(7);
        for (int i = 0; i < STAR_COUNT; i++) {
            stars[i * 3] = random.nextFloat();
            stars[i * 3 + 1] = random.nextFloat();
            stars[i * 3 + 2] = random.nextFloat() * 6.28f;
        }
        setContentDescription("OpenHub");
        // Blurred glows need software drawing before Android 9.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        unit = Math.min(w, h) / UNITS_ACROSS;
        cx = w / 2f;
        cy = h / 2f;
        branchPath.reset();
        branchPath.moveTo(x(44f), y(40f));
        branchPath.lineTo(x(44f), y(68f));
        branchPath.moveTo(x(66f), y(46f));
        branchPath.cubicTo(x(66f), y(58f), x(44f), y(54f), x(44f), y(64f));
        blur = unit > 0 ? new BlurMaskFilter(unit * 3f, BlurMaskFilter.Blur.NORMAL) : null;
        glowPaint.setMaskFilter(blur);
    }

    private float x(float logoX) {
        return cx + (logoX - GLYPH_CX) * unit;
    }

    private float y(float logoY) {
        return cy + (logoY - GLYPH_CY) * unit;
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        running = isVisible;
        if (running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (unit <= 0) return;
        boolean animate = ValueAnimator.areAnimatorsEnabled();
        float t = animate ? (SystemClock.uptimeMillis() - startTime) / 1000f : 2.2f;

        drawNebula(canvas, t);
        drawStars(canvas, t);
        // The orbit is a tilted ellipse: comets on its far side pass behind the glyph.
        drawOrbit(canvas, t, false);
        drawBranches(canvas, t);
        drawNodes(canvas, t);
        drawOrbit(canvas, t, true);

        if (animate && running && isShown()) postInvalidateOnAnimation();
    }

    private void drawNebula(Canvas canvas, float t) {
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 1.3f);
        float radius = 40f * unit * pulse;
        int inner = hueShift(0xFF7C3AED, t * 12f);
        int middle = hueShift(0xFF2563EB, t * 12f);
        glowPaint.setMaskFilter(null);
        glowPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{withAlpha(inner, 120), withAlpha(middle, 60), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, glowPaint);
        glowPaint.setShader(null);
        glowPaint.setMaskFilter(blur);
    }

    private void drawStars(Canvas canvas, float t) {
        float w = getWidth();
        float h = getHeight();
        for (int i = 0; i < STAR_COUNT; i++) {
            float sx = stars[i * 3] * w;
            float sy = stars[i * 3 + 1] * h;
            float twinkle = 0.5f + 0.5f * (float) Math.sin(t * 2.4f + stars[i * 3 + 2]);
            fillPaint.setColor(withAlpha(Color.WHITE, (int) (40 + 200 * twinkle)));
            canvas.drawCircle(sx, sy, unit * (0.5f + 0.9f * twinkle), fillPaint);
        }
    }

    private void drawBranches(Canvas canvas, float t) {
        float shift = (t * 0.25f) % 1f;
        int a = hueShift(0xFF5EEAD4, t * 40f);
        int b = hueShift(0xFFF472B6, t * 40f);
        int c = hueShift(0xFFFBBF24, t * 40f);
        Shader shader = new LinearGradient(x(40f), y(30f + 40f * shift), x(70f), y(80f + 40f * shift),
                new int[]{a, b, c, a}, null, Shader.TileMode.MIRROR);

        strokePaint.setShader(shader);
        strokePaint.setStrokeWidth(5.5f * unit * 2.2f);
        strokePaint.setAlpha(70);
        strokePaint.setMaskFilter(blur);
        canvas.drawPath(branchPath, strokePaint);

        strokePaint.setMaskFilter(null);
        strokePaint.setAlpha(255);
        strokePaint.setStrokeWidth(5.5f * unit);
        canvas.drawPath(branchPath, strokePaint);
        strokePaint.setShader(null);
    }

    private void drawNodes(Canvas canvas, float t) {
        for (int i = 0; i < NODES.length; i++) {
            float nx = x(NODES[i][0]);
            float ny = y(NODES[i][1]);
            int color = hueShift(NODE_COLORS[i], t * 40f);
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.2f + i * 2.1f);

            glowPaint.setColor(withAlpha(color, (int) (110 + 110 * pulse)));
            canvas.drawCircle(nx, ny, NODE_R * unit * (1.5f + 0.4f * pulse), glowPaint);

            fillPaint.setShader(new RadialGradient(nx - unit * 2f, ny - unit * 2f, NODE_R * unit * 1.4f,
                    Color.WHITE, color, Shader.TileMode.CLAMP));
            canvas.drawCircle(nx, ny, NODE_R * unit, fillPaint);
            fillPaint.setShader(null);

            // A small moon circling each node.
            double angle = t * (2.6 + i * 0.5) + i * 2.0;
            float orbit = NODE_R * unit * 1.75f;
            float mx = nx + (float) Math.cos(angle) * orbit;
            float my = ny + (float) Math.sin(angle) * orbit * 0.6f;
            fillPaint.setColor(withAlpha(Color.WHITE, 230));
            canvas.drawCircle(mx, my, unit * 1.3f, fillPaint);
        }
    }

    /**
     * Tilted orbit around the whole glyph with three comets. Called twice: once for
     * the half behind the logo and once for the half in front of it.
     */
    private void drawOrbit(Canvas canvas, float t, boolean front) {
        float rx = 40f * unit;
        float ry = 13f * unit;
        float tilt = -28f + 6f * (float) Math.sin(t * 0.4f);

        canvas.save();
        canvas.rotate(tilt, cx, cy);
        if (!front) {
            orbitPaint.setStrokeWidth(unit * 0.8f);
            orbitPaint.setColor(withAlpha(hueShift(0xFF93C5FD, t * 20f), 90));
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, orbitPaint);
        }
        for (int i = 0; i < COMET_COLORS.length; i++) {
            double head = t * 1.1 + i * (Math.PI * 2 / COMET_COLORS.length);
            int color = hueShift(COMET_COLORS[i], t * 30f);
            for (int s = TRAIL_STEPS; s >= 0; s--) {
                double angle = head - s * 0.07;
                boolean inFront = Math.sin(angle) > 0;
                if (inFront != front) continue;
                float px = cx + (float) Math.cos(angle) * rx;
                float py = cy + (float) Math.sin(angle) * ry;
                float fade = 1f - s / (float) (TRAIL_STEPS + 1);
                // Comets behind the logo are a bit smaller and dimmer (depth).
                float depth = inFront ? 1f : 0.7f;
                fillPaint.setColor(withAlpha(color, (int) (230 * fade * fade * depth)));
                canvas.drawCircle(px, py, unit * (0.6f + 2.0f * fade) * depth, fillPaint);
                if (s == 0) {
                    glowPaint.setColor(withAlpha(color, (int) (200 * depth)));
                    canvas.drawCircle(px, py, unit * 4f * depth, glowPaint);
                    fillPaint.setColor(Color.WHITE);
                    canvas.drawCircle(px, py, unit * 1.4f * depth, fillPaint);
                }
            }
        }
        canvas.restore();
    }

    private int hueShift(int color, float degrees) {
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + degrees) % 360f;
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
