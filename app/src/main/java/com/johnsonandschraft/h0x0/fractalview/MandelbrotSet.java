package com.johnsonandschraft.h0x0.fractalview;

import android.graphics.Color;

/**
 * Created by h0x0 on 11/2/15.
 *
 */
public class MandelbrotSet implements FractalView.FractalSet{
    private static final String DEBUG_TAG = "MandelbrotSet";

    private static final int MAX_ITERATIONS = 1024;
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
}
