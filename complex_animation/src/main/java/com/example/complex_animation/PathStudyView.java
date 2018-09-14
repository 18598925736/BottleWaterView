package com.example.complex_animation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.View;

/**
 * 这里主要看看，怎么画弧形的同心弧形；
 * <p>
 * 由于弧形是用 矩阵来定位的，那么如果我要画同心矩阵，定位了同心矩阵，才能定位同心弧形
 */
public class PathStudyView extends View {

    public PathStudyView(Context context) {
        this(context, null);
    }

    public PathStudyView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PathStudyView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaint();
    }

    private Paint mPaint;
    private static final int DEFAULT_BOTTLE_COLOR = 0XFFCEFCFF;

    private int paintWidth = 10;

    private void initPaint() {
        mPaint = new Paint();
        mPaint.setStrokeWidth(paintWidth);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setColor(DEFAULT_BOTTLE_COLOR);
        mPaint.setAntiAlias(true);

    }

    private int sweepAngle = 90;
    private int startAngle = 0;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Path path = new Path();

        RectF rectF = new RectF();
        rectF.set(0 + paintWidth, 0 + paintWidth, getWidth() - paintWidth, getHeight() - paintWidth);

        path.addArc(rectF, startAngle, sweepAngle);
        canvas.drawPath(path, mPaint);

        //如果我还想画一个同心的椭圆呢？
        rectF = new RectF();
        float margin = paintWidth * 3;
        rectF.set(0 + margin, 0 + margin, getWidth() - margin, getHeight() - margin);

        path.addArc(rectF, startAngle, sweepAngle);
        canvas.drawPath(path, mPaint);
    }
}
