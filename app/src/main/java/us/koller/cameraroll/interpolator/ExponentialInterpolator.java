package us.koller.cameraroll.interpolator;

import android.view.animation.Interpolator;

public class ExponentialInterpolator implements Interpolator {
    private double base = .005;

    public ExponentialInterpolator() {
    }

    public ExponentialInterpolator(double base) {
        this.base = base;
    }

    @Override
    public float getInterpolation(float v) {
        return (float) (1 - Math.pow(base, v) + base);
    }
}
