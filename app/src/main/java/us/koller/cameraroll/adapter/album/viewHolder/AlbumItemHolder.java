package us.koller.cameraroll.adapter.album.viewHolder;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import us.koller.cameraroll.R;
import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.util.Util;

public abstract class AlbumItemHolder extends RecyclerView.ViewHolder {
    protected static final String TAG = AlbumItemHolder.class.getSimpleName();

    public AlbumItem albumItem;
    private boolean selected = false;
    private Drawable selectorOverlay;
    static int crossFadeDuration = 150;

    AlbumItemHolder(View itemView) {
        super(itemView);
        addIndicatorDrawable(itemView);
    }

    public AlbumItem getAlbumItem() {
        return albumItem;
    }

    public void setAlbumItem(AlbumItem albumItem) {
        if (this.albumItem == albumItem) {
            return;
        }

        this.albumItem = albumItem;
        ImageView imageView = itemView.findViewById(R.id.image);
        loadImage(imageView, albumItem);
    }

    private void addIndicatorDrawable(View itemView) {
        int indicatorRes = getIndicatorDrawableResource();
        if (indicatorRes != -1) {
            final ImageView imageView = itemView.findViewById(R.id.image);
            final Drawable indicatorOverlay
                    = ContextCompat.getDrawable(itemView.getContext(), indicatorRes);
            imageView.post(() -> {
                final int overlayPadding = (int) (imageView.getWidth() * 0.05f);
                final int overlayDimens = (int) (imageView.getWidth() * 0.3f);
                assert indicatorOverlay != null;
                indicatorOverlay.setBounds(
                        imageView.getWidth() - overlayDimens - overlayPadding,
                        imageView.getHeight() - overlayDimens,
                        imageView.getWidth() - overlayPadding,
                        imageView.getHeight());
                imageView.getOverlay().add(indicatorOverlay);
            });
        }
    }

    int getIndicatorDrawableResource() {
        return -1;
    }

    public void loadImage(final ImageView imageView, final AlbumItem albumItem) {
        Glide.with(imageView.getContext())
                .asBitmap()
                .load(albumItem.getPath())
                .transition(BitmapTransitionOptions.withCrossFade(crossFadeDuration))
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Bitmap> target, boolean isFirstResource) {
                        albumItem.error = true;
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .apply(albumItem.getGlideRequestOptions(imageView.getContext()))
                .into(imageView);
    }

    public void setSelected(boolean selected) {
        setSelected(selected, true);
    }

    public void setSelected(boolean selected, boolean animate) {
        if (this.selected != selected) {
            this.selected = selected;
            updateSelected(animate);
        }
    }

    private void updateSelected(boolean animate) {
        final View imageView = itemView.findViewById(R.id.image);

        float scale = selected ? 0.8f : 1f;
        /**
         * Don't touch 'itemView' directly.
         * {@link androidx.recyclerview.widget.DefaultItemAnimator} might cancel animations added on itemView */
        View child = ((ViewGroup) itemView).getChildAt(0);
        if (animate) {
            child.animate().setInterpolator(SubsamplingScaleImageView.Companion.getInterpolator())
                    .scaleX(scale).scaleY(scale).start();
        } else {
            child.clearAnimation();
            child.setScaleX(scale);
            child.setScaleY(scale);
        }


        imageView.getOverlay().clear();
        if (selected) {
            imageView.post(() -> {
                if (selectorOverlay == null) {
                    selectorOverlay = Util.getAlbumItemSelectorOverlay(imageView.getContext());
                    int size = imageView.getWidth();
                    selectorOverlay.setBounds(0, 0, size, size);
                }
                imageView.getOverlay().add(selectorOverlay);
            });
        }
    }
}
