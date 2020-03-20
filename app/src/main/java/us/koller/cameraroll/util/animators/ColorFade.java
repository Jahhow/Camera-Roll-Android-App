package us.koller.cameraroll.util.animators;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.drawable.DrawableCompat;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import us.koller.cameraroll.data.Settings;

public class ColorFade {
    final static String TAG = ColorFade.class.getSimpleName();

    private static AnimatorSet toolbarTitleAnimSet;

    public interface ToolbarTitleFadeCallback {
        void setTitle(Toolbar toolbar);
    }

    public static void fadeBackgroundColor(final View v, final int startColor, final int endColor) {
        ValueAnimator animator = getDefaultValueAnimator();
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int color = getAnimatedColor(startColor, endColor,
                        valueAnimator.getAnimatedFraction());
                v.setBackgroundColor(color);
            }
        });
        animator.start();
    }

    private static ValueAnimator getDefaultValueAnimator() {
        return getDefaultValueAnimator(0, 100);
    }

    private static ValueAnimator getDefaultValueAnimator(int startValue, int endValue) {
        ValueAnimator animator = ValueAnimator.ofInt(startValue, endValue);
        animator.setDuration(500);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        return animator;
    }

    private static int getAnimatedColor(int startColor, int endColor, float animatedValue) {
        int alpha = getAnimatedValue(Color.alpha(startColor), Color.alpha(endColor), animatedValue);
        int red = getAnimatedValue(Color.red(startColor), Color.red(endColor), animatedValue);
        int green = getAnimatedValue(Color.green(startColor), Color.green(endColor), animatedValue);
        int blue = getAnimatedValue(Color.blue(startColor), Color.blue(endColor), animatedValue);
        return Color.argb(alpha, red, green, blue);
    }

    private static int getAnimatedValue(int start, int end, float animatedValue) {
        return (int) (start + (end - start) * animatedValue);
    }

    public static void animateToAlpha(float alpha, final View view) {
        if (!Settings.getInstance(view.getContext()).showAnimations()) {
            return;
        }
        view.animate().setInterpolator(SubsamplingScaleImageView.Companion.getInterpolator()).alpha(alpha).start();
    }

    //fade Toolbar title text change
    public static void fadeToolbarTitleColor(final Toolbar toolbar, final int color,
                                             final ToolbarTitleFadeCallback callback) {

        if (toolbarTitleAnimSet != null) {
            toolbarTitleAnimSet.cancel();
        }

        //final int transparent = ContextCompat.getColor(toolbar.getContext(), android.R.color.transparent);
        final int transparent = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color));

        TextView titleView = null;
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View v = toolbar.getChildAt(i);
            if (v instanceof TextView) {
                titleView = (TextView) v;
                break;
            }
        }
        final TextView finalTextView = titleView;

        ValueAnimator fadeOut = null;
        if (finalTextView != null) {
            fadeOut = getDefaultValueAnimator();
            fadeOut.setDuration(250);
            fadeOut.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    finalTextView.setAlpha(1 - valueAnimator.getAnimatedFraction());
                }
            });
            fadeOut.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (callback != null) {
                        callback.setTitle(toolbar);
                    }
                }
            });
        } else {
            toolbar.setTitleTextColor(transparent);
            callback.setTitle(toolbar);
        }

        ValueAnimator fadeIn = getDefaultValueAnimator();
        fadeIn.setDuration(250);
        if (finalTextView != null) {
            fadeIn.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    finalTextView.setAlpha(1.0f);
                }
            });
        }
        fadeIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                toolbar.setTitleTextColor(getAnimatedColor(transparent, color,
                        valueAnimator.getAnimatedFraction()));
            }
        });

        toolbarTitleAnimSet = new AnimatorSet();
        if (fadeOut != null) {
            toolbarTitleAnimSet.playSequentially(fadeOut, fadeIn);
        } else {
            toolbarTitleAnimSet.playSequentially(fadeIn);
        }
        toolbarTitleAnimSet.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        toolbarTitleAnimSet = null;
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        super.onAnimationCancel(animation);
                        toolbar.setTitleTextColor(color);
                    }
                });
        toolbarTitleAnimSet.start();
    }

    public static void fadeDrawableColor(final Drawable d, final int startColor, final int endColor) {
        DrawableCompat.wrap(d);
        ValueAnimator animator = getDefaultValueAnimator();
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int animatedColor = getAnimatedColor(startColor, endColor,
                        valueAnimator.getAnimatedFraction());
                DrawableCompat.setTint(d, animatedColor);

            }
        });
        AnimatorSet set = new AnimatorSet();
        set.play(animator);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                DrawableCompat.unwrap(d);
            }
        });
        set.start();
    }

    public static void fadeDrawableAlpha(final Drawable d, int endAlpha) {
        ValueAnimator animator = getDefaultValueAnimator(d.getAlpha(), endAlpha);
        animator.addUpdateListener(valueAnimator -> d.setAlpha((int) valueAnimator.getAnimatedFraction()));
        animator.start();
    }
}
