package us.koller.cameraroll.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

//Solution heavily inspired by:
//https://github.com/WangDaYeeeeee/Mysplash/blob/master/app/src/main/java/com/wangdaye/mysplash/common/ui/widget/SwipeBackCoordinatorLayout.java
public class SwipeBackCoordinatorLayout extends CoordinatorLayout {
    protected static final String TAG = SwipeBackCoordinatorLayout.class.getSimpleName();
    // widget
    public OnSwipeListener listener;

    // data
    private int swipeDistance = 0;
    private int TRANSLATION_Y_FINISH_THRESHOLD;

    private int swipeDir = NULL_DIR;
    public static final int NULL_DIR = 0;
    public static final int UP_DIR = 1;
    public static final int DOWN_DIR = -1;

    public SwipeBackCoordinatorLayout(Context context) {
        super(context);
        this.initialize();
    }

    public SwipeBackCoordinatorLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.initialize();
    }

    public SwipeBackCoordinatorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.initialize();
    }

    private void initialize() {
        TRANSLATION_Y_FINISH_THRESHOLD = getResources().getDisplayMetrics().heightPixels / 5;
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int axes, int type) {
        return ((type == ViewCompat.TYPE_TOUCH) && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0) || super.onStartNestedScroll(child, target, axes, type);
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed, int type) {
        int dyConsumed = 0;
        if (swipeDistance != 0) {
            dyConsumed = onPreScroll(dy);
        }

        int[] newConsumed = new int[]{0, 0};
        super.onNestedPreScroll(target, dx, dy - dyConsumed, newConsumed, type);

        consumed[0] = newConsumed[0];
        consumed[1] = newConsumed[1] + dyConsumed;
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
        if (swipeDistance == 0) {
            int dir = dyUnconsumed < 0 ? DOWN_DIR : UP_DIR;
            if (listener.canSwipeBack(dir)) {
                onScroll(dyUnconsumed);
                dyConsumed += dyUnconsumed;
                consumed[1] += dyUnconsumed;
                dyUnconsumed = 0;
            }
        }
        super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, consumed);
    }

    @Override
    public void onStopNestedScroll(View child, int type) {
        super.onStopNestedScroll(child, type);
        if (Math.abs(swipeDistance) >= TRANSLATION_Y_FINISH_THRESHOLD) {
            swipeBack();
        } else {
            reset();
        }
    }

    private int onPreScroll(int dy) {
        int consumed;
        if (swipeDistance * (swipeDistance - dy) < 0) {
            swipeDir = NULL_DIR;
            consumed = swipeDistance;
            swipeDistance = 0;
        } else {
            consumed = dy;
            swipeDistance -= dy;
        }

        setSwipeTranslation();

        return consumed;
    }

    private void onScroll(int dy) {
        swipeDistance = -dy;
        swipeDir = swipeDistance > 0 ? DOWN_DIR : UP_DIR;

        setSwipeTranslation();
    }


    private void swipeBack() {
        if (listener != null) {
            listener.onSwipeFinish(swipeDir);
        }
    }

    private void reset() {
        swipeDir = NULL_DIR;
        if (swipeDistance != 0) {
            ResetAnimation a = new ResetAnimation(swipeDistance);
            a.setAnimationListener(resetAnimListener);
            a.setDuration(300);
            startAnimation(a);
        }
    }

    private void setSwipeTranslation() {
        setTranslationY(swipeDistance * .5f);
        if (listener != null) {
            listener.onSwipeProcess(Math.min(1, Math.abs(swipeDistance) / (float) TRANSLATION_Y_FINISH_THRESHOLD));
        }
    }

    public static int getBackgroundColor(@FloatRange(from = 0, to = 1) float fraction) {
        return Color.argb((int) (255 * (1 - fraction)), 0, 0, 0);
    }

    public static boolean canSwipeBackForThisView(View v, int dir) {
        return !v.canScrollVertically(dir);
    }

    private class ResetAnimation extends Animation {
        // data
        private int fromDistance;

        ResetAnimation(int from) {
            this.fromDistance = from;
        }

        @Override
        public void applyTransformation(float interpolatedTime, Transformation t) {
            swipeDistance = (int) (fromDistance * (1 - interpolatedTime));
            setSwipeTranslation();
        }
    }

    private Animation.AnimationListener resetAnimListener
            = new Animation.AnimationListener() {

        @Override
        public void onAnimationStart(Animation animation) {
            setEnabled(false);
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            setEnabled(true);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };

    public interface OnSwipeListener {
        boolean canSwipeBack(int dir);

        void onSwipeProcess(@FloatRange(from = 0, to = 1) float fraction);

        void onSwipeFinish(int dir);
    }

    public void setOnSwipeListener(OnSwipeListener l) {
        this.listener = l;
    }
}
