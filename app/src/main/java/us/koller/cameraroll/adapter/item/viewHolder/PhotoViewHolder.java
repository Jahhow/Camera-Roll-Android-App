package us.koller.cameraroll.adapter.item.viewHolder;

import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import us.koller.cameraroll.R;
import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.data.models.Photo;
import us.koller.cameraroll.imageDecoder.CustomImageDecoder;
import us.koller.cameraroll.imageDecoder.CustomRegionDecoder;
import us.koller.cameraroll.ui.ItemActivity;
import us.koller.cameraroll.util.ExifUtil;
import us.koller.cameraroll.util.ItemViewUtil;

public class PhotoViewHolder extends ViewHolder {
    static final String TAG = PhotoViewHolder.class.getSimpleName();

    private boolean imageViewWasBound = false;
    private boolean onSharedElementExit_called = false;
    private float initialSubsamplingScale;

    public PhotoViewHolder(AlbumItem albumItem, int position) {
        super(albumItem, position);
    }

    @Override
    public View inflateView(ViewGroup container) {
        ViewGroup v = super.inflatePhotoView(container);
        final View view = v.findViewById(R.id.subsampling);
        final View transitionView = itemView.findViewById(R.id.image);

        //hide transitionView, when config was changed
        if (albumItem instanceof Photo
                && ((Photo) albumItem).getImageViewSavedState() != null) {
            transitionView.setVisibility(View.INVISIBLE);
        }
        ItemViewUtil.bindTransitionView((ImageView) transitionView, albumItem);
        view.setVisibility(View.INVISIBLE);
        return v;
    }

    private void swapView(final boolean isReturning) {
        final View view = itemView.findViewById(R.id.subsampling);
        final View transitionView = itemView.findViewById(R.id.image);
        if (!isReturning) {
            view.setVisibility(View.VISIBLE);
            bindImageView(view, transitionView);
        } else {
            view.setVisibility(View.INVISIBLE);
            transitionView.setVisibility(View.VISIBLE);
        }
    }

    void bindImageView(View view, final View transitionView) {
        if (albumItem.error) {
            transitionView.setVisibility(View.VISIBLE);
            ItemViewUtil.bindTransitionView((ImageView) transitionView, albumItem);
            return;
        }

        if (imageViewWasBound) {
            return;
        }

        final SubsamplingScaleImageView imageView = (SubsamplingScaleImageView) view;

        // use custom decoders
        imageView.setBitmapDecoderFactory(CustomImageDecoder::new);
        imageView.setRegionDecoderFactory(CustomRegionDecoder::new);

        //imageView.setMinimumTileDpi(196);
        //imageView.setMinimumDpi(80);
        //imageView.setDoubleTapZoomDpi(196);
        //imageView.setDoubleTapZoomScale(1.0f);

        int orientation = ExifUtil.getExifOrientationAngle(view.getContext(), albumItem);
        //imageView.setOrientation(orientation);

        imageView.setOnClickListener(PhotoViewHolder.this::imageOnClick);

        /*ImageViewState imageViewState = null;
        if (photo.getImageViewSavedState() != null) {
            imageViewState = (ImageViewState) photo.getImageViewSavedState();
            photo.putImageViewSavedState(null);
        }*/

        imageView.setImage(albumItem.getPath(), tileEnabled());
        imageView.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
            @Override
            public void onImageLoaded() {
                // onImageLoaded() might be called after onSharedElementExit()
                if (!onSharedElementExit_called)
                    transitionView.setVisibility(View.INVISIBLE);

                initialSubsamplingScale = imageView.getScale();
                imageViewWasBound = true;
                PhotoViewHolder.this.onImageLoaded();
            }

            @Override
            public void onImageLoadError(Exception e) {
                PhotoViewHolder.this.onImageLoadError();
            }
        });
    }

    @Override
    public void onSharedElementEnter() {
        swapView(false);
    }

    @Override
    public void onSharedElementExit(final ItemActivity.Callback callback) {
        onSharedElementExit_called = true;

        if (imageViewWasBound) {
            final SubsamplingScaleImageView view = itemView.findViewById(R.id.subsampling);
            final ImageView transitionView = itemView.findViewById(R.id.image);

            PointF center = view.sourceToViewCoord(new PointF(view.getSWidth() / 2f, view.getSHeight() / 2f));
            transitionView.setTranslationX(center.x - view.getWidth() / 2f);
            transitionView.setTranslationY(center.y - view.getHeight() / 2f);

            float scaleXY = view.getScale() / initialSubsamplingScale;
            transitionView.setScaleX(scaleXY);
            transitionView.setScaleY(scaleXY);
        }
        swapView(true);
        callback.done();
    }

    @Override
    public void onDestroy() {
        final SubsamplingScaleImageView imageView = itemView.findViewById(R.id.subsampling);
        if (imageView != null) {
            imageView.recycle();
        }
        super.onDestroy();
    }

    void onImageLoaded() {
    }

    void onImageLoadError() {
    }

    boolean tileEnabled(){
        return true;
    }
}
