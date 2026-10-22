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
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * "Digital rain" for the side menu: columns of green glyphs slowly falling on black
 * behind the account header, the head of each column bright, its trail fading out. The
 * first {@link #LEFT_COLS} and the last {@link #RIGHT_COLS} columns keep falling down
 * the edges of the menu, under the header, over the menu's own background. A dark band at the bottom of the header
 * keeps the name and the bio readable. The edge columns carry several drops at once, so
 * they are never empty for long. Other screens (Settings, About, Help) use the same rain
 * behind their title, with their own edge columns ({@link #setEdgeColumns}).
 *
 * The view lies behind the menu list and follows the header when the list scrolls. It
 * only animates while it is on screen (the menu is open) and draws one still frame when
 * the system's animations are turned off.
 */
public class MatrixRainView extends View {

    static final String GLYPHS =
            "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789Z:.=*+-<>|";
    private static final int BACKGROUND = 0xFF000000;
    private static final int HEAD_COLOR = 0xFFD8FFE0;
    private static final int TRAIL_COLOR = 0x00FF41;
    /** Columns at the edges that go on falling below the header (narrow enough to miss the icons). */
    private int leftCols = 1;
    private int rightCols = 3;
    /** Strength of the edge columns below the header, in percent. */
    private int edgeAlpha = 75;
    /** Rows of menu per drop in an edge column. */
    private static final int ROWS_PER_DROP = 16;
    private static final long FRAME_MS = 50L;
    /** Rows a second. */
    private static final float MIN_SPEED = 1.2f;
    private static final float MAX_SPEED = 3.6f;
    /** Share of the glyphs that change every second. */
    private static final float MUTATION_PER_SEC = 0.05f;

    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backPaint = new Paint();
    private final Paint shadePaint = new Paint();
    private final Random random = new Random();
    private final char[] oneChar = new char[1];
    private final int[] headerPos = new int[2];
    private final int[] ownPos = new int[2];
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::invalidate;

    private final float cellW;
    private final float cellH;
    private final float baseline;
    private int cols;
    private int rows;
    /** Glyph per cell, column-major. */
    private char[] cells = new char[0];
    /** Drops: one per header column, several per edge column. */
    private int[] dropCol = new int[0];
    private float[] head = new float[0];
    private float[] speed = new float[0];
    private int[] trail = new int[0];
    private boolean seeded;
    private float pendingMutations;

    @Nullable private View header;
    private int shadeTop = -1;
    private int shadeBottom = -1;
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
        backPaint.setColor(BACKGROUND);
        cellW = textSize * 0.95f;
        cellH = textSize * 1.15f;
        Paint.FontMetrics fm = glyphPaint.getFontMetrics();
        baseline = (cellH - fm.ascent - fm.descent) / 2f;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * How many columns at the left and right edges keep falling below the header, and how
     * strong they are there (percent). The side menu uses 1, 3 and 75.
     */
    public void setEdgeColumns(int left, int right, int alphaPercent) {
        leftCols = Math.max(0, left);
        rightCols = Math.max(0, right);
        edgeAlpha = Math.max(0, Math.min(100, alphaPercent));
        if (getWidth() > 0 && getHeight() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        invalidate();
    }

    /** The header the full-width rain stays behind. */
    public void setHeader(@Nullable View header) {
        this.header = header;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cols = Math.max(1, (int) Math.ceil(w / cellW));
        rows = Math.max(1, (int) Math.ceil(h / cellH));
        cells = new char[cols * rows];
        for (int i = 0; i < cells.length; i++) cells[i] = randomGlyph();
        int edgeDrops = Math.max(2, rows / ROWS_PER_DROP);
        int drops = 0;
        for (int c = 0; c < cols; c++) drops += isStrip(c) ? edgeDrops : 1;
        dropCol = new int[drops];
        int d = 0;
        for (int c = 0; c < cols; c++) {
            int n = isStrip(c) ? edgeDrops : 1;
            for (int i = 0; i < n; i++) dropCol[d++] = c;
        }
        head = new float[drops];
        speed = new float[drops];
        trail = new int[drops];
        seeded = false;
        shadeTop = -1;
    }

    private boolean isStrip(int c) {
        return c < leftCols || c >= cols - rightCols;
    }

    /** Last row a column falls to: the header's bottom, or the whole height for the strip. */
    private int limit(int c, int headerRows) {
        return isStrip(c) ? rows : Math.max(1, headerRows);
    }

    private void resetDrop(int d, int headerRows) {
        speed[d] = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED);
        trail[d] = 5 + random.nextInt(Math.max(4, Math.min(14, headerRows)));
        head[d] = -random.nextFloat() * Math.max(2, headerRows / 2f);
    }

    private char randomGlyph() {
        return GLYPHS.charAt(random.nextInt(GLYPHS.length()));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(scrollListener);
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
        getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int headerTop = 0;
        int headerBottom = 0;
        if (header != null && header.isAttachedToWindow() && header.getHeight() > 0) {
            header.getLocationInWindow(headerPos);
            getLocationInWindow(ownPos);
            headerTop = headerPos[1] - ownPos[1];
            headerBottom = headerTop + header.getHeight();
        }
        // rows of the header in the rain's grid (the header's place when the list is at the top)
        int headerRows = header != null && header.getHeight() > 0
                ? (int) Math.ceil(header.getHeight() / cellH) : rows;

        if (!seeded && cols > 0) {
            for (int d = 0; d < dropCol.length; d++) {
                resetDrop(d, headerRows);
                // spread over the column's height, so even the first frame is full
                head[d] = random.nextFloat() * (limit(dropCol[d], headerRows) + trail[d]);
            }
            seeded = true;
        }

        boolean animate = ValueAnimator.areAnimatorsEnabled();
        long now = SystemClock.uptimeMillis();
        if (animate && running) step(Math.min(0.2f, (now - lastFrame) / 1000f), headerRows);
        lastFrame = now;

        if (headerBottom > 0) {
            canvas.drawRect(0, Math.max(0, headerTop), width, headerBottom, backPaint);
        }
        // the grid scrolls with the header, so its rain moves with it
        float shift = headerTop;
        float stripLeft = (cols - rightCols) * cellW;
        for (int d = 0; d < dropCol.length; d++) {
            int c = dropCol[d];
            boolean strip = isStrip(c);
            int top = (int) Math.floor(head[d]);
            float x = c * cellW + cellW / 2f;
            for (int k = 0; k <= trail[d]; k++) {
                int r = top - k;
                if (r < 0) break;
                if (r >= rows) continue;
                float y = r * cellH + shift;
                // outside the header only the strip is drawn
                if (!strip && (y < headerTop || y + cellH > headerBottom)) continue;
                if (y + cellH < 0 || y > getHeight()) continue;
                if (k == 0) {
                    glyphPaint.setColor(HEAD_COLOR);
                } else {
                    float fade = 1f - (float) k / (trail[d] + 1);
                    int alpha = (int) (40 + 215 * fade * fade);
                    // a bit softer over the menu, so it does not fight with the items
                    if (strip && y >= headerBottom) alpha = alpha * edgeAlpha / 100;
                    glyphPaint.setColor((alpha << 24) | TRAIL_COLOR);
                }
                oneChar[0] = cells[c * rows + r];
                canvas.drawText(oneChar, 0, 1, x, y + baseline, glyphPaint);
            }
        }
        if (headerBottom > 0) {
            if (shadeTop != headerTop || shadeBottom != headerBottom) {
                shadeTop = headerTop;
                shadeBottom = headerBottom;
                float from = headerTop + (headerBottom - headerTop) * 0.35f;
                shadePaint.setShader(new LinearGradient(0, from, 0, headerBottom,
                        0x00000000, 0xCC000000, Shader.TileMode.CLAMP));
            }
            canvas.drawRect(0, Math.max(0, headerTop), Math.min(width, stripLeft), headerBottom, shadePaint);
        }

        if (animate && running && isShown()) postInvalidateDelayed(FRAME_MS);
    }

    private void step(float dt, int headerRows) {
        for (int d = 0; d < dropCol.length; d++) {
            head[d] += speed[d] * dt;
            if (head[d] - trail[d] > limit(dropCol[d], headerRows)) resetDrop(d, headerRows);
        }
        pendingMutations += cells.length * MUTATION_PER_SEC * dt;
        int changes = (int) pendingMutations;
        pendingMutations -= changes;
        for (int i = 0; i < changes; i++) {
            cells[random.nextInt(cells.length)] = randomGlyph();
        }
    }
}
