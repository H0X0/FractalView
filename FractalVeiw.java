package com.johnsonandschraft.h0x0.fractalviewer;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Created by h0x0 on 10/30/15.
 *
 */
public class FractalView extends View{
    @SuppressWarnings("UnusedDeclaration")
    private static final String DEBUG_TAG = "FractalView";

    private Matrix overall, cur, temp;
    private float[] mValues = new float[9];
    private RectF bounds, content;

    private Paint paint;
    private int[] colors, histogram, pallet;
    private Hashtable<Integer, Integer> colorMap;
    private FractalSet set = new MandelbrotSet();

    private List<FractalRegion> regions;
    private RegionAnimator regionAnimator;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private boolean showPos = false;
    private static final String POS_TEXT = "Position: (%f,%f), Zoom: %f";
    private Paint posPaint;
    private Rect posRect;

    public interface FractalSet {
        int calculate(float x, float y);
        int maxVal();
    }

    protected GestureDetector.OnGestureListener gestureListener
            = new GestureDetector.OnGestureListener() {
        private FlingAnimation flingAnimation;
        @Override
        public boolean onDown(MotionEvent e) {
            if (flingAnimation != null) flingAnimation.cancel();
            regionAnimator.cancel();
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            showPos = true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            cur.postTranslate(-distanceX, -distanceY);
            _clamp();
            FractalView.this.invalidate();
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            cur.reset();
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            flingAnimation = new FlingAnimation(velocityX, velocityY);
            compatPostOnAnimation(flingAnimation);
            return false;
        }
    };

    protected ScaleGestureDetector.OnScaleGestureListener scaleGestureListener
            = new ScaleGestureDetector.OnScaleGestureListener() {
        private float scaleBefore;
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float postScale = 1.0f + (detector.getScaleFactor() - scaleBefore);
            cur.postScale(postScale, postScale, detector.getFocusX(), detector.getFocusY());
            scaleBefore = detector.getScaleFactor();
            FractalView.this.invalidate();
            return false;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            cur.getValues(mValues);
            scaleBefore = detector.getScaleFactor();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            _updateContent();
        }
    };

    private class FlingAnimation implements Runnable {
        private OverScroller scroller;
        int curX, curY;

        public FlingAnimation(float velocityX, float velocityY) {
            cur.getValues(mValues);
            curX = (int) mValues[Matrix.MTRANS_X];
            curY = (int) mValues[Matrix.MTRANS_Y];

            final int vX = (int) velocityX;
            final int vY = (int) velocityY;

            RectF dst = new RectF(content);
            _clamp(dst);

            final int localMinX = (int) (dst.left - bounds.left);
            final int localMaxX = (int) (dst.right - bounds.right);
            final int localMinY = (int) (dst.top - bounds.bottom);
            final int localMaxY = (int) (dst.bottom - bounds.bottom);

            scroller = new OverScroller(getContext(), new AccelerateDecelerateInterpolator());

            scroller.fling(curX, curY, vX, vY, localMinX, localMaxX, localMinY, localMaxY);
        }

        @Override
        public void run() {
            if (scroller.isFinished()) {
                scroller = null;
                _updateContent();
            } else if (scroller.computeScrollOffset()) {
                final int dx = scroller.getCurrX() - curX;
                final int dy = scroller.getCurrY() - curY;
                cur.postTranslate(dx, dy);
                curX += dx;
                curY += dy;
                _clamp();
                FractalView.this.invalidate();
                compatPostOnAnimation(this);
            }
        }

        public void cancel() {
            scroller = null;
        }
    }

    private void _updateContent() {
        cur.invert(temp);
        temp.postConcat(overall);
        overall.set(temp);
        cur.reset();
        for (FractalRegion region : regions) region.setDecimation(START_DECIMATION);
    }

    private static final int START_DECIMATION = 3;
    private static final int MIN_DECIMATION = 1;

    private class FractalRegion {
        private Bitmap bitmap;
        private RectF bounds;
        private int offset, stride, decimation, calcWidth, calcHeight;
        private boolean rendered;
        private RegionCalculateTask task;

        public FractalRegion(RectF bounds) {
            this.bounds = bounds;
            stride = ((int) content.width()) >> MIN_DECIMATION;
            offset = stride * (((int) (bounds.top - content.top)) >> MIN_DECIMATION)
                    + (((int) (bounds.left - content.left)) >> MIN_DECIMATION);
            decimation = 0;
        }

        public void cancel() {
            if (task != null) task.cancel(true);
        }

        public void setDecimation(int decimation) {
            if (task != null) task.cancel(true);

            this.decimation = decimation;
            calcWidth = ((int) bounds.width()) >> decimation;
            calcWidth = calcWidth > 0 ? calcWidth : 1;
            calcHeight = ((int) bounds.height()) >> decimation;
            calcHeight = calcHeight > 0 ? calcHeight : 1;

            rendered = false;
            task = new RegionCalculateTask(this);
            task.execute();
        }

        public void setDecimation() {
            setDecimation(--decimation);
        }

        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            rendered = true;
            task = null;
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public RectF getBounds() {
            return bounds;
        }


        public boolean isRendered() {
            return rendered;
        }

        public int getOffset() {
            return offset;
        }

        public int getStride() {
            return stride;
        }

        public int getCalcWidth() {
            return calcWidth;
        }

        public int getCalcHeight() {
            return calcHeight;
        }

        public int getDecimation() {
            return decimation;
        }
    }

    private class RegionAnimator implements Runnable {
        private RegionBitmapTask task;

        @Override
        public void run() {
            boolean renderComplete = true;
            for (FractalRegion region : regions) {
                if (!region.isRendered()) {
                    renderComplete = false;
                    continue;
                }

                if (region.getDecimation() > MIN_DECIMATION) {
                    region.setDecimation();
                    renderComplete = false;
                }
            }

            if (renderComplete) {
                task = new RegionBitmapTask();
                task.execute();
            }
            FractalView.this.invalidate();
        }

        public void cancel() {
            if (task != null) task.cancel(true);
            for (FractalRegion region : regions) region.cancel();
        }
    }

    private class RegionCalculateTask extends AsyncTask<Void, Integer, Void> {
        private FractalRegion region;

        public RegionCalculateTask(FractalRegion region) {
            this.region = region;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            compatPostOnAnimation(regionAnimator);
        }

        @Override
        protected Void doInBackground(Void... params) {
            int value, index;
            float[] point = new float[2];

            final int offset = region.getOffset();
            final int stride = region.getStride();
            final int w = region.getCalcWidth();
            final int h = region.getCalcHeight();
            final float left = region.getBounds().left;
            final float top = region.getBounds().top;
            final float hStep = region.getBounds().width() / w;
            final float vStep = region.getBounds().height() / h;

            for (int i = 0; i < h; i++) {
                if (isCancelled()) break;
                for (int j = 0; j < w; j++) {
                    if (isCancelled()) break;
                    point[0] = left + j * hStep;
                    point[1] = top + i * vStep;
                    overall.mapPoints(point);

                    index = offset + stride * i + j;
                    value = set.calculate(point[0], point[1]);

                    synchronized (this) {
                        int color = colors[value];
                        int iteration;
                        if (colorMap.containsKey(color)) {
                            iteration = colorMap.get(color);
                            if (histogram[iteration] > 0) histogram[iteration]--;
                        }
                        histogram[value]++;

                        colors[index] = pallet[value];
                    }
                }
            }

            synchronized (this) {
                region.setBitmap(Bitmap.createBitmap(colors, offset, stride, w, h,
                        Bitmap.Config.RGB_565));
            }

//            regionAnimator.enqueueRegion(region);
            return null;
        }
    }

    private class RegionBitmapTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {

            float total = 0.0f;
            for (int n : histogram) total += n;

            synchronized (this) {
                colorMap.clear();
                for (int i = 0; i < pallet.length; i++) {
                    float k = 0.0f;
                    for (int j = 0; j <= i; j++) {
                        k += histogram[j];
                    }

                    float r = k / total;
                    float hue = (600.0f - (r * 260.0f)) % 360.0f;
                    float sat = 1.0f - _saturationKnockOut(r);
                    float val = 1.0f - _valueKnockOut(r);

//                    recolor[i] = pallet[i];
                    int color = Color.HSVToColor(new float[]{hue, sat, val});
                    colorMap.put(pallet[i], i);
                    pallet[i] = color;
                }
            }

            synchronized (this) {
                for (int i = 0 ; i < colors.length; i++) {
                    if (isCancelled()) return null;

                    if (!colorMap.containsKey(colors[i])) continue;
                    colors[i] = pallet[colorMap.get(colors[i])];
                }
            }

            if (isCancelled()) return null;
            synchronized (this) {
                for (FractalRegion region : regions) {
                    int offset = region.getOffset();
                    int stride = region.getStride();
                    int w = region.getCalcWidth();
                    int h = region.getCalcHeight();

                if (region.bounds.contains(bounds.centerX() - 1.0f, bounds.bottom)) {
                    region.setBitmap(Bitmap.createBitmap(pallet, pallet.length >> 5, 32, Bitmap.Config.RGB_565));
                } else if(region.bounds.contains(bounds.centerX(), bounds.bottom)) {
                    region.setBitmap(Bitmap.createBitmap(histogram, histogram.length >> 5, 32, Bitmap.Config.RGB_565));
                }else  {
                        region.setBitmap(Bitmap.createBitmap(colors, offset, stride, w, h,
                                Bitmap.Config.RGB_565));
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            compatPostOnAnimation(regionAnimator);
        }

        private float _saturationKnockOut(float r) {
            final double sigma = 0.0875, mu = 1.0 / 6.0;
            final double sqrt2Pi = Math.sqrt(2.0 * Math.PI);
            double k = Math.exp(-1.0 * (((r - mu) * (r - mu)) / (2.0 * sigma * sigma))) /
                    (sigma * sqrt2Pi);
            return ((float) (k * 0.1));
        }

        private float _valueKnockOut(float r) {
            final double alpha = 12, beta = 0.45;
            float k = ((float) (beta * Math.exp(-alpha * r)));
            return r == 1 ? 1f : 0f;
        }
    }

    public FractalView(Context context) {
        this(context, null, 0);
    }

    public FractalView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FractalView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        gestureDetector = new GestureDetector(getContext(), gestureListener);
        scaleGestureDetector = new ScaleGestureDetector(getContext(), scaleGestureListener);

        paint = new Paint();
        paint.setAntiAlias(true);
        cur = new Matrix();

        histogram = new int[set.maxVal() + 1];
        pallet = new int[set.maxVal() + 1];
        colorMap = new Hashtable<>(set.maxVal() + 1);

        double alpha = 200.0, beta = 0.75, alpha2 = 15.0;

        for (int i = 0; i < pallet.length; i++) {
            float f = ((float) i) / ((float) set.maxVal());
            float exp = 1.0f - ((float) Math.exp(-alpha2 * f));
            pallet[i] = Color.HSVToColor(new float[] {
                    (600.0f - (exp * 260.0f)) % 360.0f,
                    ((float) Math.cos(f * Math.PI / 2.0)),
                    i == pallet.length - 1 ? 0 :
                            ((float) (beta + (1 - beta) * (1.0 - Math.exp(-alpha * f))))
            });
            colorMap.put(pallet[i], i);
        }

        temp = new Matrix();
        posPaint = new Paint();
        posPaint.setColor(Color.WHITE);
        posPaint.setAntiAlias(true);
        posPaint.setTextAlign(Paint.Align.LEFT);
        posPaint.setTextSize(20f);
        posRect = new Rect();
    }

    private static final int DIVISIONS = 6;
    private static final float EXCESS = 0.3f;
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        bounds = new RectF(0f, 0f, w, h);
        content = new RectF(
                -((float) Math.floor(w * EXCESS)),
                -((float) Math.floor(h * EXCESS)),
                w + (float) Math.floor(w * EXCESS),
                h + (float) Math.floor(h * EXCESS)
        );
        final int k = (int) content.width();
        final int l = (int) content.height();
        colors = new int[(k * l) >> MIN_DECIMATION];

        overall = new Matrix();
        final float sX = 3.5f / w;
        final float sY = 2f / h;
        overall.postTranslate(-w / 2, -h / 2);
        overall.postScale(sY < sX ? sX : sY, sY < sX ? sX : sY);
        overall.postTranslate(-0.25f, 0);

        regions = new ArrayList<>();
        FractalRegion temp;
        float dx = content.width() / DIVISIONS;
        float dy = content.height() / DIVISIONS;
        regionAnimator = new RegionAnimator();

        for (int i = 0; i < DIVISIONS; i++) {
            for (int j = 0; j < DIVISIONS; j++) {
                temp = new FractalRegion(new RectF(
                        content.left + j * dx,
                        content.top + i * dy,
                        content.left + (j + 1) * dx,
                        content.top + (i + 1) * dy
                ));
                temp.setBitmap(Bitmap.createBitmap(pallet, pallet.length >> 5, 32,
                        Bitmap.Config.RGB_565));
                regions.add(temp);
                temp.setDecimation(START_DECIMATION);
            }
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        return super.onSaveInstanceState();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();
        canvas.concat(cur);
        for (FractalRegion region : regions) {
            canvas.drawBitmap(region.getBitmap(), null, region.getBounds(), paint);
        }
        canvas.restore();

        if (showPos) {
            cur.invert(temp);
            temp.postConcat(overall);
            temp.getValues(mValues);
            float posX = mValues[Matrix.MTRANS_X];
            float posY = mValues[Matrix.MTRANS_Y];
            float zoom = mValues[Matrix.MSCALE_X];
            String s = String.format(POS_TEXT, posX, posY, zoom);
            posPaint.getTextBounds(s, 0, s.length(), posRect);
            canvas.drawText(s, posRect.width(),posRect.height(), posPaint);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        showPos = false;
        boolean tf = gestureDetector.onTouchEvent(event);
        tf = tf || scaleGestureDetector.onTouchEvent(event);
        return tf || super.onTouchEvent(event);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void compatPostOnAnimation(Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            postOnAnimation(action);
        } else {
            postDelayed(action, 60 / 1000);
        }
    }

    private void _clamp(RectF dst) {
        cur.mapRect(dst);
        cur.getValues(mValues);
        final float ax = dst.left > bounds.left ? bounds.left - dst.left :
                dst.right < bounds.right ? bounds.right - dst.right : 0.0f;
        final float ay = dst.top > bounds.top ? bounds.top - dst.top :
                dst.bottom < bounds.bottom ? bounds.bottom - dst.bottom : 0.0f;
        cur.postTranslate(ax, ay);
    }

    private void _clamp() {
        RectF dst = new RectF(content);
        _clamp(dst);
    }
}
