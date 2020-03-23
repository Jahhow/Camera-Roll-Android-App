package us.koller.cameraroll.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;

import us.koller.cameraroll.R;

public class ParallaxImageView extends androidx.appcompat.widget.AppCompatImageView {
    static final String TAG = ParallaxImageView.class.getSimpleName();

    public static final String RECYCLER_VIEW_TAG = "RECYCLER_VIEW_TAG";

    private final int MAX_PARALLAX_OFFSET = (int) getContext().getResources().getDimension(R.dimen.parallax_image_view_offset);

    RecyclerView recyclerView;
    private int recyclerView_height = -1;
    private int[] recyclerView_location = {-1, -1};

    public ParallaxImageView(Context context) {
        super(context);
    }

    public ParallaxImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ParallaxImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec + MAX_PARALLAX_OFFSET);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        View view = getRootView().findViewWithTag(RECYCLER_VIEW_TAG);
        if (view instanceof RecyclerView) {
            Log.i(TAG, "onAttachedToWindow addOnScrollListener");
            recyclerView = (RecyclerView) view;
            recyclerView.addOnScrollListener(scrollListener);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        Log.i(TAG, "onLayout W,H: " + getWidth() + ", " + getHeight());
        super.onLayout(changed, left, top, right, bottom);
        if (recyclerView != null) {
            recyclerView_height = recyclerView.getHeight();
            recyclerView.getLocationOnScreen(recyclerView_location);
            setParallaxTranslation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Log.i(TAG, "onDetachedFromWindow");
        recyclerView.removeOnScrollListener(scrollListener);
        recyclerView = null;
    }

    public void setParallaxTranslation() {
        if (!isAttachedToWindow()) {
            Log.w(TAG, "setParallaxTranslation is NOT AttachedToWindow");
            return;
        }

        int[] location = new int[2];
        getLocationOnScreen(location);
        Log.i(TAG, "LocationOnScreen " + Arrays.toString(location));

        float _height = getHeight() - MAX_PARALLAX_OFFSET;

        boolean visible = location[1] + _height > recyclerView_location[1]
                || location[1] < recyclerView_location[1] + recyclerView_height;

        if (!visible) {
            Log.w(TAG, "setParallaxTranslation view is invisible");
            return;
        }

        float dy = ((location[1] + _height / 2f) - (recyclerView_location[1] + recyclerView_height / 2f))
                / (recyclerView_height + _height);
        float translationY = -MAX_PARALLAX_OFFSET * dy;
        setTranslationY(translationY - MAX_PARALLAX_OFFSET / 2f);
        Log.i(TAG, "setParallaxTranslation");
    }

    RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            setParallaxTranslation();
            Log.i(TAG, "recyclerView offset y " + recyclerView.computeVerticalScrollOffset());
        }
    };
}
