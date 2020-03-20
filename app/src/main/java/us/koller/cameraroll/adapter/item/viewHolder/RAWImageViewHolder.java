package us.koller.cameraroll.adapter.item.viewHolder;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.davemorrissey.labs.subscaleview.DecoderFactory;
import com.davemorrissey.labs.subscaleview.ImageDecoder;
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.imageDecoder.RAWImageRegionDecoder;

public class RAWImageViewHolder extends PhotoViewHolder {

    private ProgressBar progressBar;
    private boolean imageLoaded = false;

    public RAWImageViewHolder(AlbumItem albumItem, int position) {
        super(albumItem, position);
    }

    @Override
    void bindImageView(SubsamplingScaleImageView scaleImageView, final View transitionView) {
        addProgressBar();
        super.bindImageView(scaleImageView, transitionView);
    }

    @Override
    void onImageLoaded() {
        imageLoaded = true;
        removeProgressBar();
    }

    @Override
    void onImageLoadError() {
        removeProgressBar();
    }

    private void addProgressBar() {
        if (!imageLoaded && progressBar == null) {
            ViewGroup itemView = (ViewGroup) this.itemView;
            progressBar = new ProgressBar(itemView.getContext());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            itemView.addView(progressBar, params);
        }
    }

    private void removeProgressBar() {
        ViewGroup itemView = (ViewGroup) this.itemView;
        if (progressBar != null && itemView != null) {
            itemView.removeView(progressBar);
            progressBar = null;
        }
    }

    @Override
    DecoderFactory<? extends ImageDecoder> getImageDecoderFactory() {
        return null; // force use RAWImageRegionDecoder (prevents from loading twice for the same RAW file)
    }

    @Override
    DecoderFactory<? extends ImageRegionDecoder> getImageRegionDecoderFactory() {
        return RAWImageRegionDecoder::new;
    }
}
