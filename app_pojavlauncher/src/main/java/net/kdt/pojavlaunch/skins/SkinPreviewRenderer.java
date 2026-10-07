package net.kdt.pojavlaunch.skins;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/** Flat front/back view of a whole skin, drawn pixel by pixel without filtering. */
public class SkinPreviewRenderer {
    private static final int WIDTH = 16;
    private static final int HEIGHT = 32;

    private final Paint mPaint = new Paint();
    private final Rect mSrc = new Rect();
    private final Rect mDst = new Rect();
    private int mScale;
    private int mUnit;

    public SkinPreviewRenderer() {
        mPaint.setFilterBitmap(false);
        mPaint.setAntiAlias(false);
        mPaint.setDither(false);
    }

    /**
     * @param pixelSize size of one skin pixel on the output bitmap
     * @return the preview, or null if the bitmap is not a skin
     */
    public Bitmap render(Bitmap skin, boolean slim, boolean back, int pixelSize) {
        if(skin == null || skin.getWidth() < 64 || skin.getWidth() % 64 != 0) return null;
        mScale = skin.getWidth() / 64;
        mUnit = pixelSize;
        boolean legacy = skin.getHeight() * 2 == skin.getWidth();
        int arm = slim ? 3 : 4;

        Bitmap out = Bitmap.createBitmap(WIDTH * pixelSize, HEIGHT * pixelSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        int leftArmX = 4 - arm;
        int rightArmX = 12;

        if(!back) {
            part(canvas, skin, 8, 8, 8, 8, 4, 0, false);
            part(canvas, skin, 20, 20, 8, 12, 4, 8, false);
            part(canvas, skin, 44, 20, arm, 12, leftArmX, 8, false);
            part(canvas, skin, 4, 20, 4, 12, 4, 20, false);
            if(legacy) {
                part(canvas, skin, 44, 20, arm, 12, rightArmX, 8, true);
                part(canvas, skin, 4, 20, 4, 12, 8, 20, true);
            } else {
                part(canvas, skin, 36, 52, arm, 12, rightArmX, 8, false);
                part(canvas, skin, 20, 52, 4, 12, 8, 20, false);
                part(canvas, skin, 20, 36, 8, 12, 4, 8, false);
                part(canvas, skin, 44, 36, arm, 12, leftArmX, 8, false);
                part(canvas, skin, 52, 52, arm, 12, rightArmX, 8, false);
                part(canvas, skin, 4, 36, 4, 12, 4, 20, false);
                part(canvas, skin, 4, 52, 4, 12, 8, 20, false);
            }
            part(canvas, skin, 40, 8, 8, 8, 4, 0, false);
        } else {
            // Seen from behind, the right arm and leg are on the viewer's right
            part(canvas, skin, 24, 8, 8, 8, 4, 0, false);
            part(canvas, skin, 32, 20, 8, 12, 4, 8, false);
            part(canvas, skin, 48 + arm, 20, arm, 12, rightArmX, 8, false);
            part(canvas, skin, 12, 20, 4, 12, 8, 20, false);
            if(legacy) {
                part(canvas, skin, 48 + arm, 20, arm, 12, leftArmX, 8, true);
                part(canvas, skin, 12, 20, 4, 12, 4, 20, true);
            } else {
                part(canvas, skin, 40 + arm, 52, arm, 12, leftArmX, 8, false);
                part(canvas, skin, 28, 52, 4, 12, 4, 20, false);
                part(canvas, skin, 32, 36, 8, 12, 4, 8, false);
                part(canvas, skin, 48 + arm, 36, arm, 12, rightArmX, 8, false);
                part(canvas, skin, 56 + arm, 52, arm, 12, leftArmX, 8, false);
                part(canvas, skin, 12, 36, 4, 12, 8, 20, false);
                part(canvas, skin, 12, 52, 4, 12, 4, 20, false);
            }
            part(canvas, skin, 56, 8, 8, 8, 4, 0, false);
        }
        return out;
    }

    private void part(Canvas canvas, Bitmap skin, int sx, int sy, int w, int h, int dx, int dy, boolean mirror) {
        mSrc.set(sx * mScale, sy * mScale, (sx + w) * mScale, (sy + h) * mScale);
        if(mSrc.bottom > skin.getHeight() || mSrc.right > skin.getWidth()) return;
        mDst.set(dx * mUnit, dy * mUnit, (dx + w) * mUnit, (dy + h) * mUnit);
        if(!mirror) {
            canvas.drawBitmap(skin, mSrc, mDst, mPaint);
            return;
        }
        canvas.save();
        canvas.scale(-1, 1, mDst.exactCenterX(), mDst.exactCenterY());
        canvas.drawBitmap(skin, mSrc, mDst, mPaint);
        canvas.restore();
    }
}
