package net.kdt.pojavlaunch.customcontrols.handleview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import git.artdeell.mojo.R;

public class DrawerPullButton extends View {
    public DrawerPullButton(Context context) {super(context); init();}
    public DrawerPullButton(Context context, @Nullable AttributeSet attrs) {super(context, attrs); init();}

    private final Paint mBackgroundPaint = new Paint();
    private VectorDrawableCompat mDrawable;

    private void init(){
        mDrawable = VectorDrawableCompat.create(getContext().getResources(), R.drawable.ic_sharp_settings_24, null);
        setAlpha(0.6f);
        mBackgroundPaint.setColor(Color.BLACK);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawArc(getPaddingLeft(),-getHeight() + getPaddingBottom(),getWidth() - getPaddingRight(), getHeight() - getPaddingBottom(), 0, 180, true, mBackgroundPaint);

        // The outline gear needs the whole half circle to stay readable
        int size = getHeight() - getPaddingBottom() / 2;
        int left = (getWidth() - size) / 2;
        mDrawable.setBounds(left, -size / 8, left + size, size - size / 8);
        mDrawable.draw(canvas);
    }

    // Move the button to the third quarter of the screen
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int parentWidth = ((View) getParent()).getWidth();
        setTranslationX((int)(parentWidth * 0.25));
    }
}
