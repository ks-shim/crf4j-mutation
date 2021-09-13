package io.github.ksshim.crf4j.mutation.lattice;

public class LSE {

    public final static int MINUS_LOG_EPSILON = 50;

    public final static double logSumExp(double x, double y, boolean flag) {
        if (flag) return y;

        double vMin = Math.min(x, y);
        double vMax = Math.max(x, y);
        if(vMax > vMin + MINUS_LOG_EPSILON) return vMax;

        return vMax + Math.log(Math.exp(vMin -vMax) + 1.0);
    }
}
