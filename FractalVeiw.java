package com.johnsonandschraft.h0x0.fractalviewer;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.OverScroller;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by h0x0 on 10/30/15.
 *
 */
public class FractalView extends View{
    @SuppressWarnings("UnusedDeclaration")
    private static final String DEBUG_TAG = "FractalView";

    private Matrix overall, cur;
    private float[] mValues = new float[9];
    private RectF bounds, content;
    private int minX, minY, maxX, maxY;

    private Paint pallet;
    private int[] colors;
    private FractalSet set = new MandelbrotSet();

    private List<Region> regions;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

    public interface FractalSet {
        int calculate(float x, float y);
    }

    protected GestureDetector.OnGestureListener gestureListener
            = new GestureDetector.OnGestureListener() {
        private FlingAnimation flingAnimation;
        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            cur.postTranslate(-distanceX, -distanceY);
            clampTranslate();
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
            float postScale = detector.getScaleFactor() / scaleBefore;
            cur.postScale(postScale, postScale, detector.getFocusX(), detector.getFocusY());
            cur.getValues(mValues);
            float scaleAfter = mValues[Matrix.MSCALE_X];
            Log.d(DEBUG_TAG, String.format("Scale before %f - Post Scale %f - Scale after %f", scaleBefore, postScale, scaleAfter));
            scaleBefore = scaleAfter;
            FractalView.this.invalidate();
            return false;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            cur.getValues(mValues);
            scaleBefore = mValues[Matrix.MSCALE_X];
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {

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

            scroller = new OverScroller(getContext(), new AccelerateDecelerateInterpolator());
            scroller.fling(curX, curY, vX, vY, minX, maxX, minY, maxY);
        }

        @Override
        public void run() {
            if (scroller.isFinished()) {
                scroller = null;
            } else if (scroller.computeScrollOffset()) {
                final int dx = scroller.getCurrX() - curX;
                final int dy = scroller.getCurrY() - curY;
                cur.postTranslate(dx, dy);
                curX = scroller.getCurrX();
                curY = scroller.getCurrY();

                FractalView.this.invalidate();
                compatPostOnAnimation(this);
            }
        }

        public void cancel() {
            scroller = null;
        }
    }

    private class AnimateResolve implements Runnable {
        private Region region;

        public AnimateResolve(Region region) {
            this.region = region;
        }

        @Override
        public void run() {
            region.resolve();
            FractalView.this.invalidate();
        }
    }

    private static final int START_DECIMATION = 3;
    private static final int MIN_DECIMATION = 0;
    private class Region {
        private Bitmap bitmap;
        private Matrix matrix;
        private RectF regionBounds;
        private int decimation;

        public Region(RectF regionBounds) {
            this.regionBounds = regionBounds;
            matrix = new Matrix();
            matrix.setTranslate(regionBounds.left, regionBounds.top);
            decimation = START_DECIMATION + 1;
        }


        public void resolve() {
            if (decimation > MIN_DECIMATION) {
                final RegionResolver resolver = new RegionResolver(this);
                resolver.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, --decimation);
            }
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            matrix.setScale(
                    regionBounds.width() / bitmap.getWidth(),
                    regionBounds.height() / bitmap.getHeight()
            );
            matrix.postTranslate(regionBounds.left, regionBounds.top);
        }

        public Matrix getMatrix() {
            return matrix;
        }

        public RectF getRegionBounds() {
            return regionBounds;
        }
    }

    private class RegionResolver extends AsyncTask<Integer, Void, Bitmap> {
        private WeakReference<Region> regionWeakReference;
        private Rect regionRect;
        private float[] tmpPoint = new float[2];
        private int offset, stride;

        public RegionResolver(Region region) {
            regionWeakReference = new WeakReference<>(region);
            regionRect = new Rect();
            region.getRegionBounds().round(regionRect);
            offset = (int) content.width() * (regionRect.top - (int) content.top)
                    + (regionRect.left - (int) content.left);
            stride = (int) content.width();
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            Region region = regionWeakReference.get();
            if (region == null) return;

            region.setBitmap(bitmap);
            compatPostOnAnimation(new AnimateResolve(region));
        }

        @Override
        protected Bitmap doInBackground(Integer... params) {
            final int dec = params[0];
            final int w = regionRect.width() >> dec < 1 ? 1 : regionRect.width() >> dec;
            final int h = regionRect.height() >> dec < 1 ? 1 : regionRect.height() >> dec;
            for (int i = 0; i < h; i++) {
                if (isCancelled()) break;
                for (int j = 0; j < w; j++) {
                    if (isCancelled()) break;
                    tmpPoint[0] = regionRect.left + j * regionRect.width() / w;
                    tmpPoint[1] = regionRect.top + i * regionRect.height() / h;
                    overall.mapPoints(tmpPoint);
                    synchronized (this) {
                        colors[offset + stride * i + j] =
                                set.calculate(tmpPoint[0], tmpPoint[1]);
                    }
                }
            }
            return Bitmap.createBitmap(colors, offset, stride, w, h, Bitmap.Config.RGB_565);
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

        pallet = new Paint();
//        pallet.setAntiAlias(true);
        cur = new Matrix();
//        regions = new ArrayList<>();
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
        colors = new int[k * l];

        minX = (int) (content.left - bounds.left);
        maxX = (int) (content.right - bounds.right);
        minY = (int) (content.top - bounds.top);
        maxY = (int) (content.bottom - bounds.bottom);

        overall = new Matrix();
        final float sX = 3.5f / w;
        final float sY = 2f / h;
        overall.setTranslate(-w / 2, -h / 2);
        overall.postScale(sY < sX ? sX : sY, sY < sX ? sX : sY);

        regions = new ArrayList<>();
        Region tmp;
        float dx = content.width() / DIVISIONS;
        float dy = content.height() / DIVISIONS;
        for (int i = 0; i < DIVISIONS; i++) {
            for (int j = 0; j < DIVISIONS; j++) {
                tmp = new Region(
                        new RectF(
                                content.left + j * dx,
                                content.top + i * dy,
                                content.left + (j + 1) * dx,
                                content.top + (i + 1) * dy
                        )
                );
//                tmp = new Region(new RectF( i * dx, j * dy, (i + 1) * dx, (j + 1) * dy));
                tmp.setBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.hourglass));
//                tmp.resolve();
                regions.add(tmp);
            }
        }

        for (Region region : regions) region.resolve();
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
        for (Region region : regions) {
            canvas.drawBitmap(region.getBitmap(), region.getMatrix(), pallet);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
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

    private void clampTranslate() {
        cur.getValues(mValues);
        mValues[Matrix.MTRANS_X] = mValues[Matrix.MTRANS_X] < maxX ?
                mValues[Matrix.MTRANS_X] > minX ?
                        mValues[Matrix.MTRANS_X] : minX : maxX;
        mValues[Matrix.MTRANS_Y] = mValues[Matrix.MTRANS_Y] < maxY ?
                mValues[Matrix.MTRANS_Y] > minY ?
                        mValues[Matrix.MTRANS_Y] : minY : maxY;
        cur.setValues(mValues);
    }
}
