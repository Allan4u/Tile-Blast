package com.allan.tileblast.game;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.allan.tileblast.R;
import com.allan.tileblast.audio.AudioManager;
import com.allan.tileblast.storage.StorageManager;

public class GameView extends View {

    public interface GameCallback {
        void onGameOver(int finalScore);
        void onPauseRequested();
    }

    // Game mode
    private int boardSize;
    private int handSize;
    private String modeName;

    // Game state
    private Board board;
    private Hand hand;
    private ScoreManager scoreManager;
    private boolean gameOver = false;
    private boolean paused = false;

    // Drag state
    private int draggingIndex = -1;
    private float dragX, dragY;
    private int hoverGridX = -1, hoverGridY = -1;

    // Layout
    private int blockSize;
    private int gridLeft, gridTop;
    private int handY;
    private float handBlockSize;
    private RectF[] handRects;

    // Shake
    private float shakeX = 0, shakeY = 0;
    private long shakeEndTime = 0;
    private float shakeIntensity = 0;

    // Combo visual
    private String comboText = null;
    private long comboEndTime = 0;

    // Drawing
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Typeface fontRegular, fontBold;

    // Callback
    private GameCallback callback;
    private AudioManager audioManager;
    private StorageManager storageManager;
    private int bestScore = 0;

    // Pause button
    private RectF pauseBtnRect = new RectF();

    // Density for dp-to-px conversion
    private float density;

    public GameView(Context context) { super(context); init(context); }
    public GameView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }

    private void init(Context ctx) {
        fontRegular = ResourcesCompat.getFont(ctx, R.font.silkscreen);
        fontBold = ResourcesCompat.getFont(ctx, R.font.silkscreen_bold);
        audioManager = AudioManager.getInstance(ctx);
        storageManager = new StorageManager(ctx);
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.BLACK);
    }

    public void setup(int boardSize, int handSize, String modeName, GameCallback cb) {
        this.boardSize = boardSize;
        this.handSize = handSize;
        this.modeName = modeName;
        this.callback = cb;
        this.board = new Board(boardSize);
        this.hand = new Hand(handSize);
        this.scoreManager = new ScoreManager(boardSize);
        this.gameOver = false;
        this.paused = false;
        this.bestScore = storageManager.getBestScore(modeName);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateLayout(w, h);
    }

    private void calculateLayout(int w, int h) {
        // Grid block size: fit in available space
        int gridPadding = (int)(40 * density);
        int hudHeight = 120;
        int handHeight = 160;
        int availW = w - gridPadding;
        int availH = h - hudHeight - handHeight - 60;
        blockSize = Math.min(availW / boardSize, availH / boardSize);
        blockSize = Math.min(blockSize, (int)(46 * 3 * density)); // cap (density-aware)

        int gridW = blockSize * boardSize;
        int gridH = blockSize * boardSize;
        gridLeft = (w - gridW) / 2;
        gridTop = hudHeight + 20;

        // Hand area
        handY = gridTop + gridH + 30;
        handBlockSize = blockSize * 0.45f;

        // Calculate hand piece rects
        handRects = new RectF[handSize];
        float totalHandW = 0;
        float[] pieceWidths = new float[handSize];
        for (int i = 0; i < handSize; i++) {
            Piece p = hand.get(i);
            if (p != null) {
                pieceWidths[i] = p.getCols() * handBlockSize + 20;
            } else {
                pieceWidths[i] = 3 * handBlockSize + 20;
            }
            totalHandW += pieceWidths[i];
        }
        float startX = (w - totalHandW) / 2f;
        for (int i = 0; i < handSize; i++) {
            handRects[i] = new RectF(startX, handY, startX + pieceWidths[i], handY + 5 * handBlockSize);
            startX += pieceWidths[i];
        }

        // Pause button (at least 48dp touch target)
        int minPx = (int)(48 * density);
        int btnSize = Math.max(50, minPx);
        pauseBtnRect.set(w - btnSize - 10, 15, w - 10, 15 + btnSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (board == null) return;

        int w = getWidth(), h = getHeight();

        // Apply shake
        updateShake();
        canvas.save();
        canvas.translate(shakeX, shakeY);

        drawHUD(canvas, w);
        drawGrid(canvas);
        drawHandPieces(canvas, w);
        drawDraggingPiece(canvas);
        drawPauseButton(canvas, w);

        canvas.restore();

        // Overlays (no shake)
        drawComboOverlay(canvas, w, h);
        if (paused) drawPauseOverlay(canvas, w, h);
        if (gameOver) drawGameOverOverlay(canvas, w, h);

        // Keep animating if shaking
        if (System.currentTimeMillis() < shakeEndTime) invalidate();
    }

    private void drawHUD(Canvas canvas, int w) {
        // Score
        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(36);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("" + scoreManager.getScore(), w / 2f, 50, textPaint);

        // Best score
        textPaint.setTypeface(fontRegular);
        textPaint.setTextSize(16);
        textPaint.setColor(Color.GRAY);
        canvas.drawText("BEST: " + bestScore, w / 2f, 75, textPaint);

        // Combo
        if (scoreManager.getCombo() > 0) {
            textPaint.setTypeface(fontBold);
            textPaint.setTextSize(20);
            textPaint.setColor(0xFFFFD700);
            canvas.drawText("COMBO x" + scoreManager.getCombo(), w / 2f, 100, textPaint);
        }
    }

    private void drawGrid(Canvas canvas) {
        int gridW = blockSize * boardSize;
        int gridH = blockSize * boardSize;

        // Grid border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        if (draggingIndex >= 0 && hand.get(draggingIndex) != null) {
            paint.setColor(hand.get(draggingIndex).getColor());
        } else {
            paint.setColor(Color.WHITE);
        }
        canvas.drawRoundRect(gridLeft - 4, gridTop - 4, gridLeft + gridW + 4, gridTop + gridH + 4, 8, 8, paint);

        // Grid background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        canvas.drawRect(gridLeft, gridTop, gridLeft + gridW, gridTop + gridH, paint);

        // Draw cells
        for (int y = 0; y < boardSize; y++) {
            for (int x = 0; x < boardSize; x++) {
                int cx = gridLeft + x * blockSize;
                int cy = gridTop + y * blockSize;
                int cell = board.getCell(x, y);
                int colorIdx = board.getCellColor(x, y);

                if (cell == Board.FILLED || cell == Board.HOVERED_BREAK_FILLED) {
                    int ci = (cell == Board.HOVERED_BREAK_FILLED) ? board.getHoverColor(x, y) : colorIdx;
                    drawBeveledBlock(canvas, cx, cy, blockSize, ci, 1f);
                } else if (cell == Board.HOVERED) {
                    drawBeveledBlock(canvas, cx, cy, blockSize, colorIdx, 0.3f);
                } else if (cell == Board.HOVERED_BREAK_EMPTY) {
                    int ci = board.getHoverColor(x, y);
                    drawBeveledBlock(canvas, cx, cy, blockSize, ci, 0.5f);
                } else {
                    // Empty cell border
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(0.5f);
                    paint.setColor(0xFF282828);
                    canvas.drawRect(cx, cy, cx + blockSize, cy + blockSize, paint);
                }
            }
        }
    }

    private void drawBeveledBlock(Canvas canvas, int x, int y, int size, int colorIdx, float alpha) {
        int a = (int)(alpha * 255);
        int bw = Math.max(size / 6, 2); // border width

        // Fill
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Piece.getColorFromIndex(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x, y, x + size, y + size, paint);

        // Top border (light)
        paint.setColor(Piece.getTopBorder(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x, y, x + size, y + bw, paint);

        // Left border
        paint.setColor(Piece.getLeftBorder(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x, y, x + bw, y + size, paint);

        // Right border (dark)
        paint.setColor(Piece.getRightBorder(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x + size - bw, y, x + size, y + size, paint);

        // Bottom border (darkest)
        paint.setColor(Piece.getBottomBorder(colorIdx));
        paint.setAlpha(a);
        canvas.drawRect(x, y + size - bw, x + size, y + size, paint);

        paint.setAlpha(255);
    }

    private void drawHandPieces(Canvas canvas, int w) {
        if (handRects == null) return;
        calculateHandRects(w);
        for (int i = 0; i < handSize; i++) {
            if (i == draggingIndex) continue; // skip dragging one
            Piece piece = hand.get(i);
            if (piece == null) continue;
            float px = handRects[i].left + 10;
            float py = handRects[i].top + 10;
            drawPieceAt(canvas, piece, px, py, handBlockSize, 0.8f);
        }
    }

    private void calculateHandRects(int w) {
        float totalHandW = 0;
        float[] pieceWidths = new float[handSize];
        for (int i = 0; i < handSize; i++) {
            Piece p = hand.get(i);
            if (p != null) {
                pieceWidths[i] = p.getCols() * handBlockSize + 20;
            } else {
                pieceWidths[i] = 3 * handBlockSize + 20;
            }
            totalHandW += pieceWidths[i];
        }
        float startX = (w - totalHandW) / 2f;
        for (int i = 0; i < handSize; i++) {
            Piece p = hand.get(i);
            float ph = (p != null ? p.getRows() : 3) * handBlockSize + 20;
            handRects[i] = new RectF(startX, handY, startX + pieceWidths[i], handY + ph);
            startX += pieceWidths[i];
        }
    }

    private void drawPieceAt(Canvas canvas, Piece piece, float px, float py, float bs, float alpha) {
        for (int dy = 0; dy < piece.getRows(); dy++) {
            for (int dx = 0; dx < piece.getCols(); dx++) {
                if (piece.matrix[dy][dx] == 1) {
                    int bx = (int)(px + dx * bs);
                    int by = (int)(py + dy * bs);
                    drawBeveledBlock(canvas, bx, by, (int)bs, piece.colorIndex, alpha);
                }
            }
        }
    }

    private void drawDraggingPiece(Canvas canvas) {
        if (draggingIndex < 0) return;
        Piece piece = hand.get(draggingIndex);
        if (piece == null) return;

        float bs = blockSize; // full size when dragging
        float px = dragX - (piece.getCols() * bs) / 2f;
        float py = dragY - (piece.getRows() * bs) - (int)(40 * density); // offset above finger

        drawPieceAt(canvas, piece, px, py, bs, 0.85f);
    }

    private void drawPauseButton(Canvas canvas, int w) {
        int minPx = (int)(48 * density);
        int btnSize = Math.max(50, minPx);
        pauseBtnRect.set(w - btnSize - 10, 15, w - 10, 15 + btnSize);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x1AFFFFFF);
        canvas.drawRoundRect(pauseBtnRect, 12, 12, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(pauseBtnRect, 12, 12, paint);

        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(22);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("II", pauseBtnRect.centerX(), pauseBtnRect.centerY() + 8, textPaint);
    }

    private void drawComboOverlay(Canvas canvas, int w, int h) {
        if (comboText == null) return;
        if (System.currentTimeMillis() > comboEndTime) { comboText = null; return; }

        float alpha = Math.min(1f, (comboEndTime - System.currentTimeMillis()) / 500f);
        int a = (int)(alpha * 255);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33FFD700);
        paint.setAlpha(a);
        float tw = textPaint.measureText(comboText) + 80;
        float cx = (w - tw) / 2f;
        float cy = h * 0.3f;
        RectF r = new RectF(cx, cy, cx + tw, cy + 60);
        canvas.drawRoundRect(r, 20, 20, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0xFFFFD700);
        paint.setAlpha(a);
        canvas.drawRoundRect(r, 20, 20, paint);

        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(38);
        textPaint.setColor(Color.WHITE);
        textPaint.setAlpha(a);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(comboText, w / 2f, cy + 43, textPaint);
        textPaint.setAlpha(255);
        paint.setAlpha(255);

        invalidate(); // animate fade out
    }

    private void drawPauseOverlay(Canvas canvas, int w, int h) {
        // Dim background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB3000000);
        canvas.drawRect(0, 0, w, h, paint);

        // Container
        float cw = Math.min(w * 0.85f, 400);
        float ch = 350;
        float cx = (w - cw) / 2f;
        float cy = (h - ch) / 2f;
        paint.setColor(0xFA0A0A0A);
        canvas.drawRoundRect(cx, cy, cx + cw, cy + ch, 24, 24, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0x4D969696);
        canvas.drawRoundRect(cx, cy, cx + cw, cy + ch, 24, 24, paint);

        // Title
        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(42);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("PAUSED", w / 2f, cy + 70, textPaint);

        // Buttons
        drawButton(canvas, w / 2f, cy + 140, "Resume", 0xFF00FF00);
        drawButton(canvas, w / 2f, cy + 210, "Quit", 0xFFFF3333);
    }

    private void drawGameOverOverlay(Canvas canvas, int w, int h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB3000000);
        canvas.drawRect(0, 0, w, h, paint);

        float cw = Math.min(w * 0.85f, 400);
        float ch = 300;
        float cx = (w - cw) / 2f;
        float cy = (h - ch) / 2f;
        paint.setColor(0xFA0A0A0A);
        canvas.drawRoundRect(cx, cy, cx + cw, cy + ch, 24, 24, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0x4D969696);
        canvas.drawRoundRect(cx, cy, cx + cw, cy + ch, 24, 24, paint);

        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(42);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("GAME OVER", w / 2f, cy + 70, textPaint);

        textPaint.setTypeface(fontRegular);
        textPaint.setTextSize(26);
        textPaint.setColor(0xFFFFD700);
        canvas.drawText("Score: " + scoreManager.getScore(), w / 2f, cy + 120, textPaint);

        drawButton(canvas, w / 2f, cy + 200, "Back to Menu", 0xFFFF3333);
    }

    private void drawButton(Canvas canvas, float cx, float y, String text, int color) {
        float bw = 220, bh = 50;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        RectF r = new RectF(cx - bw / 2, y - bh / 2, cx + bw / 2, y + bh / 2);
        canvas.drawRoundRect(r, 12, 12, paint);

        textPaint.setTypeface(fontBold);
        textPaint.setTextSize(22);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, cx, y + 8, textPaint);
    }

    // ======== TOUCH HANDLING ========

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float tx = event.getX();
        float ty = event.getY();

        if (gameOver) {
            if (event.getAction() == MotionEvent.ACTION_UP) handleGameOverTouch(tx, ty);
            return true;
        }
        if (paused) {
            if (event.getAction() == MotionEvent.ACTION_UP) handlePauseTouch(tx, ty);
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check pause button
                if (pauseBtnRect.contains(tx, ty)) {
                    paused = true;
                    invalidate();
                    return true;
                }
                // Check hand pieces
                if (handRects != null) {
                    for (int i = 0; i < handSize; i++) {
                        if (hand.get(i) != null && handRects[i].contains(tx, ty)) {
                            draggingIndex = i;
                            dragX = tx;
                            dragY = ty;
                            invalidate();
                            return true;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (draggingIndex >= 0) {
                    dragX = tx;
                    dragY = ty;
                    updateHoverPreview();
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (draggingIndex >= 0) {
                    attemptPlacement();
                    draggingIndex = -1;
                    board.clearHover();
                    hoverGridX = -1;
                    hoverGridY = -1;
                    invalidate();
                }
                break;
        }
        return true;
    }

    private void updateHoverPreview() {
        if (draggingIndex < 0) return;
        Piece piece = hand.get(draggingIndex);
        if (piece == null) return;

        // Calculate grid position from drag coordinates
        float bs = blockSize;
        float px = dragX - (piece.getCols() * bs) / 2f;
        float py = dragY - (piece.getRows() * bs) - (int)(40 * density);

        int gx = Math.round((px - gridLeft) / bs);
        int gy = Math.round((py - gridTop) / bs);

        board.clearHover();
        if (gx >= 0 && gy >= 0 && gx + piece.getCols() <= boardSize && gy + piece.getRows() <= boardSize) {
            if (board.canPlace(piece, gx, gy)) {
                hoverGridX = gx;
                hoverGridY = gy;
                board.setHover(piece, gx, gy);
            } else {
                hoverGridX = -1;
                hoverGridY = -1;
            }
        } else {
            hoverGridX = -1;
            hoverGridY = -1;
        }
    }

    private void attemptPlacement() {
        if (hoverGridX < 0 || hoverGridY < 0) return;
        Piece piece = hand.get(draggingIndex);
        if (piece == null) return;
        if (!board.canPlace(piece, hoverGridX, hoverGridY)) return;

        // Place!
        board.clearHover();
        board.placePiece(piece, hoverGridX, hoverGridY);

        // Audio + haptic
        audioManager.playPlace();
        vibrate();

        // Score for placement
        scoreManager.addPlacement(piece.getBlockCount());

        // Break lines
        int linesBroken = board.breakLines();
        int comboLevel = scoreManager.processLineBreak(linesBroken);

        if (linesBroken > 0) {
            triggerShake(linesBroken * 8);
            if (comboLevel >= 2) {
                audioManager.playCombo(comboLevel);
                showCombo(comboLevel);
                if (comboLevel >= 4 || linesBroken >= 3) audioManager.playJuiciness();
            } else {
                audioManager.playBreak();
            }
        }

        // Update best
        if (scoreManager.getScore() > bestScore) bestScore = scoreManager.getScore();

        // Remove from hand
        hand.remove(draggingIndex);

        // Refill hand if empty
        if (hand.isEmpty()) hand.refill();

        // Check game over
        if (!board.canPlaceAny(hand.getAll())) {
            gameOver = true;
            audioManager.playGameover();
            storageManager.saveScore(scoreManager.getScore(), modeName);
            if (callback != null) callback.onGameOver(scoreManager.getScore());
        }

        calculateLayout(getWidth(), getHeight());
    }

    private void handlePauseTouch(float tx, float ty) {
        int w = getWidth(), h = getHeight();
        float cw = Math.min(w * 0.85f, 400);
        float ch = 350;
        float cx = (w - cw) / 2f;
        float cy = (h - ch) / 2f;

        // Button X bounds (buttons are 220px wide, centered at w/2)
        float bx1 = w / 2f - 110;
        float bx2 = w / 2f + 110;

        // Resume button
        if (tx > bx1 && tx < bx2 && ty > cy + 115 && ty < cy + 165) { paused = false; invalidate(); }
        // Quit button
        if (tx > bx1 && tx < bx2 && ty > cy + 185 && ty < cy + 235) { if (callback != null) callback.onPauseRequested(); }
    }

    private void handleGameOverTouch(float tx, float ty) {
        int w = getWidth(), h = getHeight();
        float cw = Math.min(w * 0.85f, 400);
        float ch = 300;
        float cx = (w - cw) / 2f;
        float cy = (h - ch) / 2f;

        // Button X bounds (buttons are 220px wide, centered at w/2)
        float bx1 = w / 2f - 110;
        float bx2 = w / 2f + 110;

        // Back to menu button
        if (tx > bx1 && tx < bx2 && ty > cy + 175 && ty < cy + 225) {
            if (callback != null) callback.onPauseRequested();
        }
    }

    // ======== EFFECTS ========

    private void triggerShake(float intensity) {
        shakeIntensity = intensity;
        shakeEndTime = System.currentTimeMillis() + 200;
        invalidate();
    }

    private void updateShake() {
        if (System.currentTimeMillis() < shakeEndTime) {
            float t = (shakeEndTime - System.currentTimeMillis()) / 200f;
            shakeX = (float)(Math.random() * 2 - 1) * shakeIntensity * t;
            shakeY = (float)(Math.random() * 2 - 1) * shakeIntensity * t;
        } else {
            shakeX = 0;
            shakeY = 0;
        }
    }

    private void showCombo(int level) {
        comboText = "COMBO x" + level + "!";
        comboEndTime = System.currentTimeMillis() + 1000;
        invalidate();
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(30);
                }
            }
        } catch (Exception e) { /* ignore */ }
    }

    public void setPaused(boolean p) { this.paused = p; invalidate(); }
    public boolean isPaused() { return paused; }
    public boolean isGameOver() { return gameOver; }

    // ======== STATE PERSISTENCE (Bug 7) ========

    public void saveState(Bundle outState) {
        if (board == null || scoreManager == null) return;

        // Save board state
        int size = board.getSize();
        int[] cellStates = new int[size * size];
        int[] cellColors = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int cell = board.getCell(x, y);
                // Only save FILLED or EMPTY (clear hover states)
                cellStates[y * size + x] = (cell == Board.FILLED || cell == Board.HOVERED_BREAK_FILLED) ? Board.FILLED : Board.EMPTY;
                cellColors[y * size + x] = board.getCellColor(x, y);
            }
        }
        outState.putIntArray("board_cells", cellStates);
        outState.putIntArray("board_colors", cellColors);
        outState.putInt("board_size", size);

        // Save hand state
        int handCount = hand.getSize();
        outState.putInt("hand_size", handCount);
        for (int i = 0; i < handCount; i++) {
            Piece p = hand.get(i);
            if (p != null) {
                outState.putInt("hand_shape_" + i, p.shapeIndex);
                outState.putInt("hand_color_" + i, p.colorIndex);
            } else {
                outState.putInt("hand_shape_" + i, -1);
            }
        }

        // Save score and combo
        outState.putInt("score", scoreManager.getScore());
        outState.putInt("combo", scoreManager.getCombo());

        // Save game flags
        outState.putBoolean("game_over", gameOver);
        outState.putBoolean("paused", paused);
        outState.putInt("best_score", bestScore);
    }

    public void restoreState(Bundle savedState) {
        if (savedState == null || board == null) return;

        int size = savedState.getInt("board_size", 0);
        if (size != board.getSize()) return;

        // Restore board
        int[] cellStates = savedState.getIntArray("board_cells");
        int[] cellColors = savedState.getIntArray("board_colors");
        if (cellStates != null && cellColors != null) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    board.setCell(x, y, cellStates[y * size + x], cellColors[y * size + x]);
                }
            }
        }

        // Restore hand
        int handCount = savedState.getInt("hand_size", 0);
        if (handCount == hand.getSize()) {
            for (int i = 0; i < handCount; i++) {
                int shapeIdx = savedState.getInt("hand_shape_" + i, -1);
                if (shapeIdx >= 0) {
                    int colorIdx = savedState.getInt("hand_color_" + i, 0);
                    // Replace hand piece via remove and direct access
                    hand.remove(i);
                    hand.getAll()[i] = new Piece(shapeIdx, colorIdx);
                } else {
                    hand.remove(i);
                }
            }
        }

        // Restore score and combo
        int score = savedState.getInt("score", 0);
        int combo = savedState.getInt("combo", 0);
        scoreManager.restoreState(score, combo);

        // Restore flags
        gameOver = savedState.getBoolean("game_over", false);
        paused = savedState.getBoolean("paused", false);
        bestScore = savedState.getInt("best_score", 0);

        calculateLayout(getWidth(), getHeight());
        invalidate();
    }
}
