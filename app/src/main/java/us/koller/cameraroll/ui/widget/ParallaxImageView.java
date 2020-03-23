package us.koller.cameraroll.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import us.koller.cameraroll.R;

public class ParallaxImageView extends androidx.appcompat.widget.AppCompatImageView {
    static final String TAG = ParallaxImageView.class.getSimpleName();

    public static final String RECYCLER_VIEW_TAG = "RECYCLER_VIEW_TAG";

    private final int MAX_PARALLAX_OFFSET = (int) getContext().getResources().getDimension(R.dimen.parallax_image_view_offset);

    View itemView;
    RecyclerView recyclerView;
    int itemView_height;
    private int recyclerView_height;

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
            itemView = (View) getParent();
            recyclerView = (RecyclerView) view;
            recyclerView.addOnScrollListener(scrollListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (recyclerView != null) {
            recyclerView.removeOnScrollListener(scrollListener);
            recyclerView = null;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (recyclerView != null) {
            recyclerView_height = recyclerView.getHeight();
            itemView_height = itemView.getHeight();
            setParallaxTranslation();
        }
    }

    public void setParallaxTranslation() {
        if (!isAttachedToWindow()) {
            return;
        }

        int itemViewTop = itemView.getTop();
        boolean visible = itemViewTop + itemView_height > 0 && itemViewTop < recyclerView_height;

        if (!visible) {
            return;
        }

        float dy = (itemViewTop + itemView_height - recyclerView_height) * .5f
                / (recyclerView_height + itemView_height);
        float translationY = -MAX_PARALLAX_OFFSET * dy;
        setTranslationY(translationY - MAX_PARALLAX_OFFSET / 2f);
    }

    RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            setParallaxTranslation();
        }
    };
}
