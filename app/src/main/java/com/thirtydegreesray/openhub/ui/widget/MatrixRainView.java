package com.thirtydegreesray.openhub.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * "Digital rain" background for the side menu header: columns of green glyphs falling
 * down on black, the head of each column bright, its trail fading out. A dark band at the
 * bottom keeps the name and the bio readable.
 *
 * It only animates while it is on screen (the menu is open), at about 30 frames a second,
 * and draws one still frame when the system's animations are turned off.
 */
public class MatrixRainView extends View {

    private static final String GLYPHS =
            "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789Z:.=*+-<>|";
    private static final int BACKGROUND = 0xFF000000;
    private static final int HEAD_COLOR = 0xFFD8FFE0;
    private static final int TRAIL_COLOR = 0x00FF41;
    private static final long FRAME_MS = 33L;
    /** Share of the glyphs that change on every frame. */
    private static final float MUTATION = 0.04f;

    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint();
    private final Random random = new Random();
    private final char[] oneChar = new char[1];

    private float cellW;
    private float cellH;
    private int cols;
    private int rows;
    /** Glyph per cell, column-major. */
    private char[] cells = new char[0];
    private float[] head = new float[0];
    private float[] speed = new float[0];
    private int[] trail = new int[0];

    private long lastFrame;
    private boolean running;

    public MatrixRainView(@NonNull Context context) {
        this(context, null);
    }

    public MatrixRainView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MatrixRainView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13,
                getResources().getDisplayMetrics());
        glyphPaint.setTextSize(textSize);
        glyphPaint.setTypeface(Typeface.MONOSPACE);
        glyphPaint.setTextAlign(Paint.Align.CENTER);
        cellW = textSize * 0.95f;
        cellH = textSize * 1.15f;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cols = Math.max(1, (int) Math.ceil(w / cellW));
        rows = Math.max(1, (int) Math.ceil(h / cellH));
        cells = new char[cols * rows];
        for (int i = 0; i < cells.length; i++) cells[i] = randomGlyph();
        head = new float[cols];
        speed = new float[cols];
        trail = new int[cols];
        for (int c = 0; c < cols; c++) {
            resetColumn(c);
            // start spread over the whole height, so even the first frame is full
            head[c] = random.nextFloat() * (rows + trail[c]);
        }
        // the dark band under the name and the bio
        shadePaint.setShader(new LinearGradient(0, h * 0.35f, 0, h,
                0x00000000, 0xCC000000, Shader.TileMode.CLAMP));
    }

    private void resetColumn(int c) {
        speed[c] = 5f + random.nextFloat() * 11f;
        trail[c] = 6 + random.nextInt(Math.max(4, rows));
        head[c] = -random.nextFloat() * rows;
    }

    private char randomGlyph() {
        return GLYPHS.charAt(random.nextInt(GLYPHS.length()));
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        running = isVisible;
        if (running) {
            lastFrame = SystemClock.uptimeMillis();
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        boolean animate = ValueAnimator.areAnimatorsEnabled();
        long now = SystemClock.uptimeMillis();
        if (animate && running) {
            float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
            step(dt);
        }
        lastFrame = now;

        canvas.drawColor(BACKGROUND);
        Paint.FontMetrics fm = glyphPaint.getFontMetrics();
        float baseline = (cellH - fm.ascent - fm.descent) / 2f;
        for (int c = 0; c < cols; c++) {
            int top = (int) Math.floor(head[c]);
            float x = c * cellW + cellW / 2f;
            for (int k = 0; k <= trail[c]; k++) {
                int r = top - k;
                if (r < 0) break;
                if (r >= rows) continue;
                if (k == 0) {
                    glyphPaint.setColor(HEAD_COLOR);
                } else {
                    float fade = 1f - (float) k / (trail[c] + 1);
                    int alpha = (int) (40 + 215 * fade * fade);
                    glyphPaint.setColor((alpha << 24) | TRAIL_COLOR);
                }
                oneChar[0] = cells[c * rows + r];
                canvas.drawText(oneChar, 0, 1, x, r * cellH + baseline, glyphPaint);
            }
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), shadePaint);

        if (animate && running && isShown()) postInvalidateDelayed(FRAME_MS);
    }

    private void step(float dt) {
        for (int c = 0; c < cols; c++) {
            head[c] += speed[c] * dt;
            if (head[c] - trail[c] > rows) resetColumn(c);
        }
        int changes = Math.max(1, (int) (cells.length * MUTATION));
        for (int i = 0; i < changes; i++) {
            cells[random.nextInt(cells.length)] = randomGlyph();
        }
    }
}
