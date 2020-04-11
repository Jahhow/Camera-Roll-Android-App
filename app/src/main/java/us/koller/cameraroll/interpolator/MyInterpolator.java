package us.koller.cameraroll.interpolator;

import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

public final class MyInterpolator {
    @NonNull
    public static Interpolator accelerateDecelerateInterpolator = new SubsamplingScaleImageView.SigmoidInterpolator(3, 3);
    public static Interpolator decelerateInterpolator = new SubsamplingScaleImageView.SigmoidInterpolator(3, 0);
}