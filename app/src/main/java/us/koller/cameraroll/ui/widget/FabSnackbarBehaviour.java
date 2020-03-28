package us.koller.cameraroll.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import us.koller.cameraroll.util.Util;

@SuppressWarnings("unused")
public class FabSnackbarBehaviour extends CoordinatorLayout.Behavior<FloatingActionButton> {
    protected static final String TAG = FabSnackbarBehaviour.class.getSimpleName();

    public FabSnackbarBehaviour(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(@NonNull CoordinatorLayout parent, @NonNull FloatingActionButton fab, View dependency) {
        return Util.SNACKBAR.equals(dependency.getTag());
    }

    private boolean showingFab = true;

    @Override
    public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent, @NonNull FloatingActionButton fab, View dependency) {
        setShowFab(dependency.getVisibility() != View.VISIBLE, fab);
        return true;
    }

    @Override
    public void onDependentViewRemoved(@NonNull CoordinatorLayout parent, @NonNull FloatingActionButton fab, @NonNull View dependency) {
        setShowFab(true, fab);
    }

    private void setShowFab(boolean show, @NonNull FloatingActionButton fab) {
        if (show != showingFab) {
            showingFab = show;
            float alpha = show ? 1 : 0;
            long duration = show ? 400 : 150;
            fab.animate().setDuration(duration).alpha(alpha).start();
        }
    }
}