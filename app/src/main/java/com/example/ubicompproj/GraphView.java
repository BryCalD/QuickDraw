package com.example.ubicompproj;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class GraphView extends View {

    private Paint paint;
    private float [] x;
    private float [] y;
    private float [] z;
    private float [] m;
    boolean drawX;
    boolean drawY;
    boolean drawZ;
    boolean drawM;
    final int MAX_POINTS = 400;
    final int ACCEL_RANGE = 2048;


    public GraphView(Context context) {
        super(context);
        init();
    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GraphView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(2);
        paint.setTextSize(25);
        paint.setStyle(Paint.Style.STROKE);
        x = new float[MAX_POINTS];
        y = new float[MAX_POINTS];
        z = new float[MAX_POINTS];
        m = new float[MAX_POINTS];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getMeasuredWidth();
        float height = getMeasuredHeight();
        paint.setColor(Color.BLACK);
        canvas.drawLine(0,height/2,width,height/2,paint);
        canvas.drawText("0", 2, height/2-2, paint);
        for(int i=0;i<MAX_POINTS-1;i++)
        {
            if (drawX) {
                paint.setColor(Color.RED);
                canvas.drawLine(i * (width / MAX_POINTS), (height-x[i] * height), (i + 1) * (width / MAX_POINTS), (height-x[i + 1] * height), paint);
            }
            if (drawY) {
                paint.setColor(Color.GREEN);
                canvas.drawLine(i * (width / MAX_POINTS), (height-y[i] * height), (i + 1) * (width / MAX_POINTS), (height-y[i + 1] * height), paint);
            }
            if (drawZ) {
                paint.setColor(Color.BLUE);
                canvas.drawLine(i * (width / MAX_POINTS), (height-z[i] * height), (i + 1) * (width / MAX_POINTS), (height-z[i + 1] * height), paint);
            }
            if (drawM) {
                paint.setColor(Color.MAGENTA);
                canvas.drawLine(i * (width / MAX_POINTS), (height-(height/2+m[i] * height/2)), (i + 1) * (width / MAX_POINTS), (height-(height/2+m[i + 1] * height/2)), paint);
            }
        }
    }

    public void updateGraph(float acc[],boolean drawX, boolean drawY, boolean drawZ, boolean drawM) {
        System.arraycopy(x, 0, x, 1,MAX_POINTS-1);
        System.arraycopy(y, 0, y, 1,MAX_POINTS-1);
        System.arraycopy(z, 0, z, 1,MAX_POINTS-1);
        System.arraycopy(m, 0, m, 1,MAX_POINTS-1);
        x[0] = (acc[0]+ACCEL_RANGE)/(ACCEL_RANGE*2);
        y[0] = (acc[1]+ACCEL_RANGE)/(ACCEL_RANGE*2);
        z[0] = (acc[2]+ACCEL_RANGE)/(ACCEL_RANGE*2);
        float mag = (float)Math.sqrt(acc[0]*acc[0]+acc[1]*acc[1]+acc[2]*acc[2]);
        float max_mag = (float)Math.sqrt(ACCEL_RANGE*ACCEL_RANGE*3);
        m[0] = mag/max_mag;
        this.drawX = drawX;
        this.drawY = drawY;
        this.drawZ = drawZ;
        this.drawM = drawM;
        invalidate();
    }

}
