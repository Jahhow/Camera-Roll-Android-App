package us.koller.cameraroll.adapter.item.viewHolder;

import android.view.View;
import android.view.ViewGroup;

import com.davemorrissey.labs.subscaleview.DecoderFactory;
import com.davemorrissey.labs.subscaleview.ImageDecoder;
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import us.koller.cameraroll.R;
import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.imageDecoder.CustomImageDecoder;
import us.koller.cameraroll.imageDecoder.CustomRegionDecoder;
import us.koller.cameraroll.imageDecoder.GlideImageDecoder;

public class PhotoViewHolder extends ViewHolder {
    protected static final String TAG = PhotoViewHolder.class.getSimpleName();

    public PhotoViewHolder(AlbumItem albumItem, int position) {
        super(albumItem, position);
    }

    @Override
    public View inflateView(ViewGroup container) {
        View view = inflatePhotoView(container);
        SubsamplingScaleImageView scaleImageView = view.findViewById(R.id.subsampling);
        loadImage(scaleImageView);
        return view;
    }

    private void loadImage(SubsamplingScaleImageView scaleImageView) {
        if (albumItem.error)
            return;
        scaleImageView.setDecoderFactory(getImageDecoderFactory());
        scaleImageView.setRegionDecoderFactory(getImageRegionDecoderFactory());
        scaleImageView.setPreviewDecoderFactory(getPreviewImageDecoderFactory());
        scaleImageView.setOnClickListener(PhotoViewHolder.this::imageOnClick);
        scaleImageView.setStartRect(albumItem.itemViewBound);
        scaleImageView.loadImage(albumItem.getUri(itemView.getContext()));
        scaleImageView.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
            @Override
            public void onImageLoaded() {
                PhotoViewHolder.this.onImageLoaded();
            }

            @Override
            public void onImageLoadError(Exception e) {
                PhotoViewHolder.this.onImageLoadError();
            }
        });
    }

    @Override
    public void onShowViewHolder() {
    }

    @Override
    public void onDestroy() {
        final SubsamplingScaleImageView imageView = itemView.findViewById(R.id.subsampling);
        if (imageView != null) {
            imageView.recycle();
        }
        super.onDestroy();
    }

    DecoderFactory<? extends ImageRegionDecoder> getImageRegionDecoderFactory() {
        return CustomRegionDecoder::new;
    }

    DecoderFactory<? extends ImageDecoder> getImageDecoderFactory() {
        return CustomImageDecoder::new;
    }

    DecoderFactory<? extends ImageDecoder> getPreviewImageDecoderFactory() {
        return GlideImageDecoder::new;
    }

    void onImageLoaded() {
    }

    void onImageLoadError() {
    }
}
