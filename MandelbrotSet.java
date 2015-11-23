package com.johnsonandschraft.h0x0.fractalviewer;

import android.graphics.Color;

/**
 * Created by h0x0 on 11/2/15.
 *
 */
public class MandelbrotSet implements FractalView.FractalSet{
    private static final String DEBUG_TAG = "MandelbrotSet";

    private final int MAX_ITERATIONS = 1024;
//    private int i;
//    double zx, zy, tmp;

    @Override
    public int calculate(float x, float y) {
        int i;

        double q =  Math.pow(x - 0.25, 2.0) + y * y;
        if (q * (q + x - 0.25) < 0.25 * y * y) return MAX_ITERATIONS;

        float x0 = 0f, y0 = 0f;
        for (i = 0; x0 * x0 + y0 * y0 < 1 << 16 && i < MAX_ITERATIONS; i++) {
            float xTemp = x0 * x0 - y0 * y0 + x;
            float yTemp = 2f * x0 * y0 + y;
            if (x0 == xTemp && y0 == yTemp) {
                i = MAX_ITERATIONS;
                break;
            }
            x0 = xTemp;
            y0 = yTemp;
        }

        if (i < MAX_ITERATIONS) {
            double log_zn = Math.log(x0 * x0 + y0 * y0);
            double nu = Math.log(log_zn / Math.log(2.0)) / Math.log(2.0);
            i += 1.0 - nu;
        }
//        return (int) (((float) MAX_ITERATIONS - i) / ((float) MAX_ITERATIONS) * (float) 0xFFFFFF);
        return i;
//        return Color.HSVToColor(new float[] {
//                i / (float) MAX_ITERATIONS * 360f + 240f,
//                1,
//                i < MAX_ITERATIONS ? 1: 0
//        });
    }

    @Override
    public int maxVal() {
        return MAX_ITERATIONS;
    }

    @Override
    public FractalView.FractalSet getInstance() {
        return new MandelbrotSet();
    }

    @Override
    public double calculate(double x, double y) {
        double i;

        double x0 = x, y0 = y;

        double q = Math.pow(x - 0.25, 2.0) + y * y;
        if (q * (q + x - 0.25) < y * y) return MAX_ITERATIONS;

        for (i = 0; x * x + y * y < 1<< 16 && i < MAX_ITERATIONS; i++) {
            double xTemp = x * x - y * y + x0;
            double yTemp = 2.0 * x0 * y0 + y0;
            if (x == xTemp && y == yTemp) { i = MAX_ITERATIONS; break; }
            x = xTemp;
            y = yTemp;
        }

        if (i < MAX_ITERATIONS) {
            double logZn = Math.log(x * x + y * y);
            double nu = Math.log(logZn / Math.log(2.0)) / Math.log(2.0);
            i += 1.0 - nu;
        }

        return i;
    }
}
