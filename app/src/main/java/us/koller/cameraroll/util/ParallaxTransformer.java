package us.koller.cameraroll.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

public class ParallaxTransformer implements ViewPager.PageTransformer {

    private static final float PARALLAX_OFFSET = 0.5f;

    @Override
    public void transformPage(@NonNull View page, float position) {
        View view = findParallaxView(page);
        if (view == null) return;
        if (-1 < position && position < 1) {
            float dx = page.getWidth() * position * PARALLAX_OFFSET;
            view.setTranslationX(-dx);
            //page.setClipBounds(new Rect((int) dx, 0, (int) dx + page.getWidth(), page.getHeight()));
        } else {
            view.setTranslationX(0);
            //page.setClipBounds(null);
        }
    }

    private View findParallaxView(View page) {
        if (page instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) page;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View v = viewGroup.getChildAt(i);
                if (v.getVisibility() == View.VISIBLE) {
                    return v;
                }
            }
        }
        return null;
    }
}
