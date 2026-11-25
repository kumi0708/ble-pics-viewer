package jp.kumi.picsar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class HudOverlayView extends View {
    private static final int BLACK = Color.rgb(3, 5, 4);
    private static final int INK = Color.rgb(233, 233, 223);
    private static final int MUTED = Color.rgb(136, 149, 139);
    private static final int ORANGE = Color.rgb(255, 138, 0);
    private static final int GREEN = Color.rgb(85, 239, 150);
    private static final int YELLOW = Color.rgb(246, 215, 47);
    private static final int RED = Color.rgb(255, 68, 58);
    private static final int BLUE = Color.rgb(102, 199, 234);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Typeface mono = Typeface.MONOSPACE;
    private final Typeface serif = Typeface.create(Typeface.SERIF, Typeface.BOLD);
    private final Typeface sans = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.JAPAN);

    private PicsParser.PicsFrame frame;
    private String source = "WAITING";
    private String status = "PICS信号を受信してください";
    private int packetCount = 0;
    private boolean stale = false;

    public HudOverlayView(Context context) {
        super(context);
        init();
    }

    public HudOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        showWaiting("PICS信号を受信してください");
    }

    public void showFrame(PicsParser.PicsFrame next, String sourceLabel, int count) {
        frame = next;
        source = sourceLabel;
        status = sourceLabel.equals("LIVE") ? "PICS Type 2 受信中" : "デモデータ表示";
        packetCount = count;
        stale = false;
        updateDescription();
        invalidate();
    }

    public void showWaiting(String message) {
        frame = null;
        source = "WAITING";
        status = message;
        stale = false;
        updateDescription();
        invalidate();
    }

    public void showStale() {
        stale = true;
        source = "STALE";
        status = "5秒以上PICS Type 2を受信していません";
        updateDescription();
        invalidate();
    }

    public void showError(String message) {
        frame = null;
        source = "ERROR";
        status = message;
        stale = true;
        updateDescription();
        invalidate();
    }

    private void updateDescription() {
        if (frame == null || stale) {
            setContentDescription("PICS AR。受信停止。" + status);
            return;
        }
        setContentDescription(
                "PICS AR。" + verdictText() + "。信号1 " + frame.signalOne.state.label +
                        "、信号2 " + frame.signalTwo.state.label + "。"
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float margin = dp(22);
        float bottomBar = dp(78);

        drawVignette(canvas, width, height, bottomBar);
        drawHeader(canvas, width, margin);
        drawSafeCorners(canvas, width, height, margin, bottomBar);
        drawReticle(canvas, width, height);
        drawRoute(canvas, width, height, bottomBar);
        drawSignalTarget(canvas, width * .20f, height * .27f, frame == null ? null : frame.signalOne, "SIGNAL 01", false);
        drawSignalTarget(canvas, width * .70f, height * .20f, frame == null ? null : frame.signalTwo, "SIGNAL 02", true);
        drawVerdict(canvas, width, height, margin, bottomBar);
        drawTelemetry(canvas, width, height, margin, bottomBar);
        drawBottomRule(canvas, width, height, margin, bottomBar);
    }

    private void drawVignette(Canvas canvas, float width, float height, float bottomBar) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, height,
                new int[]{Color.argb(210, 0, 0, 0), Color.argb(18, 0, 0, 0), Color.argb(210, 0, 0, 0)},
                new float[]{0f, .44f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(228, 3, 5, 4));
        canvas.drawRect(0, height - bottomBar, width, height, paint);
    }

    private void drawHeader(Canvas canvas, float width, float margin) {
        line(canvas, margin, dp(18), width - margin, dp(18), ORANGE, dp(2));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(ORANGE);
        canvas.save();
        canvas.rotate(45, margin + dp(10), dp(42));
        canvas.drawRect(margin + dp(3), dp(35), margin + dp(17), dp(49), paint);
        canvas.restore();
        text(canvas, "PICS / AR", margin + dp(30), dp(45), dp(18), ORANGE, sans, Paint.Align.LEFT);
        text(canvas, "PEDESTRIAN VIEW", margin + dp(30), dp(59), dp(8), MUTED, mono, Paint.Align.LEFT);

        int sourceColor = source.equals("LIVE") ? GREEN : source.equals("DEMO") ? BLUE : source.equals("WAITING") ? ORANGE : RED;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(sourceColor);
        canvas.drawRect(width * .46f, dp(34), width * .46f + dp(7), dp(41), paint);
        text(canvas, source + "  " + timeFormat.format(new Date()), width * .47f, dp(42), dp(10), INK, mono, Paint.Align.LEFT);
        text(canvas, "TARGET", width - margin, dp(38), dp(8), ORANGE, mono, Paint.Align.RIGHT);
        text(canvas, frame == null ? "--------" : frame.intersectionId, width - margin, dp(55), dp(13), INK, mono, Paint.Align.RIGHT);
    }

    private void drawSafeCorners(Canvas canvas, float width, float height, float margin, float bottomBar) {
        float top = dp(78);
        float bottom = height - bottomBar - dp(18);
        float size = dp(42);
        corner(canvas, margin, top, size, 1, 1);
        corner(canvas, width - margin, top, size, -1, 1);
        corner(canvas, margin, bottom, size, 1, -1);
        corner(canvas, width - margin, bottom, size, -1, -1);
    }

    private void corner(Canvas canvas, float x, float y, float size, int sx, int sy) {
        line(canvas, x, y, x + size * sx, y, ORANGE, dp(1.5f));
        line(canvas, x, y, x, y + size * sy, ORANGE, dp(1.5f));
    }

    private void drawReticle(Canvas canvas, float width, float height) {
        float cx = width * .5f;
        float cy = height * .36f;
        float size = dp(40);
        line(canvas, cx - size, cy, cx - dp(8), cy, Color.argb(140, 255, 138, 0), dp(1));
        line(canvas, cx + dp(8), cy, cx + size, cy, Color.argb(140, 255, 138, 0), dp(1));
        line(canvas, cx, cy - size, cx, cy - dp(8), Color.argb(140, 255, 138, 0), dp(1));
        line(canvas, cx, cy + dp(8), cx, cy + size, Color.argb(140, 255, 138, 0), dp(1));
    }

    private void drawSignalTarget(Canvas canvas, float x, float y, PicsParser.Signal signal, String label, boolean right) {
        float boxWidth = dp(150);
        float boxHeight = dp(72);
        int signalColor = signal == null ? MUTED : colorForState(signal.state);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 3, 5, 4));
        canvas.drawRect(x, y, x + boxWidth, y + boxHeight, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(ORANGE);
        canvas.drawRect(x, y, x + boxWidth, y + boxHeight, paint);
        text(canvas, label, x + dp(9), y + dp(18), dp(8), ORANGE, mono, Paint.Align.LEFT);
        text(canvas, signal == null ? "待機" : signal.state.label, x + dp(9), y + dp(57), dp(28), signalColor, serif, Paint.Align.LEFT);
        text(canvas, signal == null || signal.remaining < 0 ? "--" : String.format(Locale.US, "%02d", signal.remaining),
                x + boxWidth - dp(9), y + dp(57), dp(21), INK, mono, Paint.Align.RIGHT);

        float leaderY = y + boxHeight * .55f;
        float endpoint = right ? x - dp(72) : x + boxWidth + dp(72);
        line(canvas, right ? x : x + boxWidth, leaderY, endpoint, leaderY + dp(24), ORANGE, dp(1));
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(ORANGE);
        float markerX = endpoint;
        float markerY = leaderY + dp(24);
        canvas.save();
        canvas.rotate(45, markerX, markerY);
        canvas.drawRect(markerX - dp(4), markerY - dp(4), markerX + dp(4), markerY + dp(4), paint);
        canvas.restore();
    }

    private void drawRoute(Canvas canvas, float width, float height, float bottomBar) {
        int stateColor = verdictColor();
        float center = width * .5f;
        float top = height * .48f;
        float bottom = height - bottomBar - dp(18);
        boolean halt = frame == null || stale || "横断停止".equals(verdictText());
        paint.setAlpha(halt ? 70 : 255);
        line(canvas, center, top, center, bottom, stateColor, dp(4));
        for (int i = 0; i < 3; i++) {
            float y = bottom - dp(28) - i * dp(58);
            path.reset();
            path.moveTo(center - dp(23), y);
            path.lineTo(center, y - dp(23));
            path.lineTo(center + dp(23), y);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setStrokeCap(Paint.Cap.SQUARE);
            paint.setColor(stateColor);
            canvas.drawPath(path, paint);
        }
        paint.setAlpha(255);
    }

    private void drawVerdict(Canvas canvas, float width, float height, float margin, float bottomBar) {
        float baseline = height - bottomBar - dp(52);
        int color = verdictColor();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(margin + dp(24), baseline - dp(105), margin + dp(34), baseline + dp(4), paint);
        text(canvas, "PEDESTRIAN SIGNAL VERDICT", margin + dp(46), baseline - dp(94), dp(8), ORANGE, mono, Paint.Align.LEFT);
        float titleSize = Math.min(dp(84), width * .085f);
        text(canvas, verdictText(), margin + dp(42), baseline - dp(18), titleSize, color, serif, Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(margin + dp(44), baseline - dp(10), margin + dp(224), baseline + dp(17), paint);
        text(canvas, verdictEnglish(), margin + dp(54), baseline + dp(9), dp(10), BLACK, mono, Paint.Align.LEFT);
        text(canvas, remainingText(), margin + dp(242), baseline + dp(9), dp(12), INK, mono, Paint.Align.LEFT);
    }

    private void drawTelemetry(Canvas canvas, float width, float height, float margin, float bottomBar) {
        float right = width - margin;
        float y = height - bottomBar - dp(102);
        float left = right - dp(226);
        line(canvas, left, y, right, y, ORANGE, dp(1));
        text(canvas, "TYPE", left + dp(8), y + dp(16), dp(8), ORANGE, mono, Paint.Align.LEFT);
        text(canvas, "S1", left + dp(76), y + dp(16), dp(8), ORANGE, mono, Paint.Align.LEFT);
        text(canvas, "S2", left + dp(138), y + dp(16), dp(8), ORANGE, mono, Paint.Align.LEFT);
        text(canvas, "02", left + dp(8), y + dp(39), dp(14), INK, mono, Paint.Align.LEFT);
        text(canvas, frame == null ? "--" : String.format(Locale.US, "%02X", frame.signalOne.raw),
                left + dp(76), y + dp(39), dp(14), INK, mono, Paint.Align.LEFT);
        text(canvas, frame == null ? "--" : String.format(Locale.US, "%02X", frame.signalTwo.raw),
                left + dp(138), y + dp(39), dp(14), INK, mono, Paint.Align.LEFT);
        text(canvas, "RX", left + dp(198), y + dp(16), dp(8), ORANGE, mono, Paint.Align.LEFT);
        text(canvas, String.format(Locale.US, "%03d", packetCount), left + dp(198), y + dp(39), dp(14), INK, mono, Paint.Align.LEFT);
    }

    private void drawBottomRule(Canvas canvas, float width, float height, float margin, float bottomBar) {
        float y = height - bottomBar;
        line(canvas, margin, y, width - margin, y, ORANGE, dp(1));
        text(canvas, status, margin + dp(8), height - dp(26), dp(11), stale ? RED : MUTED, sans, Paint.Align.LEFT);
        text(canvas, "RECEIVE ONLY · 0x01CE", width * .48f, height - dp(26), dp(9), ORANGE, mono, Paint.Align.LEFT);
    }

    private String verdictText() {
        if (frame == null) return stale ? "受信停止" : "受信待機";
        if (stale) return "受信停止";
        if (frame.signalOne.state == PicsParser.SignalState.GREEN || frame.signalTwo.state == PicsParser.SignalState.GREEN) return "横断許可";
        if (frame.signalOne.state == PicsParser.SignalState.BLINK_GREEN || frame.signalTwo.state == PicsParser.SignalState.BLINK_GREEN) return "切替注意";
        return "横断停止";
    }

    private String verdictEnglish() {
        String verdict = verdictText();
        if (verdict.equals("横断許可")) {
            return frame.signalOne.state == PicsParser.SignalState.GREEN ? "ROUTE S1 / WALK" : "ROUTE S2 / WALK";
        }
        if (verdict.equals("切替注意")) return "SIGNAL / BLINKING";
        if (verdict.equals("横断停止")) return "ALL ROUTES / STOP";
        if (verdict.equals("受信停止")) return "SIGNAL DATA / STALE";
        return "SIGNAL DATA / WAITING";
    }

    private String remainingText() {
        if (frame == null || stale) return "残り --";
        PicsParser.Signal selected = frame.signalOne;
        if (frame.signalTwo.state == PicsParser.SignalState.GREEN) selected = frame.signalTwo;
        if (frame.signalOne.state != PicsParser.SignalState.GREEN && frame.signalTwo.state == PicsParser.SignalState.BLINK_GREEN) selected = frame.signalTwo;
        return selected.remaining < 0 ? "残り --" : String.format(Locale.JAPAN, "残り %02d", selected.remaining);
    }

    private int verdictColor() {
        String verdict = verdictText();
        if (verdict.equals("横断許可")) return GREEN;
        if (verdict.equals("切替注意")) return YELLOW;
        if (verdict.equals("横断停止") || verdict.equals("受信停止")) return RED;
        return ORANGE;
    }

    private int colorForState(PicsParser.SignalState state) {
        if (state == PicsParser.SignalState.GREEN) return GREEN;
        if (state == PicsParser.SignalState.BLINK_GREEN) return YELLOW;
        if (state == PicsParser.SignalState.RED) return RED;
        return MUTED;
    }

    private void line(Canvas canvas, float x1, float y1, float x2, float y2, int color, float width) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.SQUARE);
        paint.setColor(color);
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    private void text(Canvas canvas, String value, float x, float y, float size, int color, Typeface typeface, Paint.Align align) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(typeface);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setColor(color);
        canvas.drawText(value, x, y, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
