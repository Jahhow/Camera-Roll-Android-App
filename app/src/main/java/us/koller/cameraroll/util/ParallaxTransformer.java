package us.koller.cameraroll.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

public class ParallaxTransformer implements ViewPager.PageTransformer {
    private static final float PARALLAX_OFFSET = 0.5f;

    @Override
    public void transformPage(@NonNull View page, float position) {
        View view = findParallaxView(page);
        if (view == null)
            return;
        float dx = page.getWidth() * position * PARALLAX_OFFSET;
        view.setTranslationX(-dx);
    }

    @Nullable
    private View findParallaxView(View page) {
        if (page instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) page;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    return child;
                }
            }
        }
        return null;
    }
}