package us.koller.cameraroll.util;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ScrollIndicatorAdaptor extends RecyclerView.OnScrollListener {
    private final View scrollIndicatorTop;
    private final View scrollIndicatorBottom;

    public ScrollIndicatorAdaptor(RecyclerView recyclerView, View scrollIndicatorTop, View scrollIndicatorBottom) {
        this.scrollIndicatorTop = scrollIndicatorTop;
        this.scrollIndicatorBottom = scrollIndicatorBottom;
        recyclerView.addOnScrollListener(this);
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        scrollIndicatorTop.setVisibility(
                recyclerView.canScrollVertically(-1) ?
                        View.VISIBLE : View.INVISIBLE);

        scrollIndicatorBottom.setVisibility(
                recyclerView.canScrollVertically(1) ?
                        View.VISIBLE : View.INVISIBLE);
    }
}
