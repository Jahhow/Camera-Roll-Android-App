package us.koller.cameraroll.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import us.koller.cameraroll.R;
import us.koller.cameraroll.adapter.item.viewHolder.GifViewHolder;
import us.koller.cameraroll.data.models.AlbumItem;

public class ItemViewUtil {
    static final String TAG = ItemViewUtil.class.getSimpleName();

    public static View inflatePhotoView(ViewGroup container) {
        return LayoutInflater.from(container.getContext())
                .inflate(R.layout.photo_view, container, false);
    }

    public static View inflateGifView(ViewGroup container) {
        return LayoutInflater.from(container.getContext())
                .inflate(R.layout.gif_view, container, false);
    }

    public static ViewGroup inflateVideoView(ViewGroup container) {
        return (ViewGroup) LayoutInflater.from(container.getContext())
                .inflate(R.layout.video_view, container, false);
    }

    public static void bindTransitionView(final ImageView imageView, final AlbumItem albumItem) {
        ViewCompat.setTransitionName(imageView, albumItem.getPath());
        Context context = imageView.getContext();
        Glide.with(context)
                .asBitmap()
                .load(albumItem.getUri(context))
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Bitmap> target, boolean isFirstResource) {
                        albumItem.error = true;
                        if (albumItem.isSharedElement
                                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            albumItem.isSharedElement = false;
                            ((AppCompatActivity) imageView.getContext())
                                    .startPostponedEnterTransition();
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        if (albumItem.isSharedElement
                                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            albumItem.isSharedElement = false;
                            ((AppCompatActivity) imageView.getContext())
                                    .startPostponedEnterTransition();
                        }
                        return false;
                    }
                })
                .apply(albumItem.getGlideRequestOptions(imageView.getContext()))
                .into(imageView);
    }

    /*public static void setPreviewImage(final SubsamplingScaleImageView scaleImageView, final AlbumItem albumItem) {
        Context context = scaleImageView.getContext();
        int orientationAngle = ExifUtil.getExifOrientationAngle(context, albumItem);
        Glide.with(context)
                .asBitmap()
                .load(albumItem.getUri(context))
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<Bitmap> target, boolean isFirstResource) {
                        //Log.i(TAG, "onLoadFailed");
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        scaleImageView.setPreviewImage(resource, orientationAngle);
                        return true;
                    }
                })
                .apply(albumItem.getGlideRequestOptions(context).transform(new Rotate(-orientationAngle)))
                .submit(1440 / 4, 2560 / 4);
        //Log.i(TAG, "scaleImageView.getInnerWidth(), getInnerHeight() " + scaleImageView.getInnerWidth() + ", " + scaleImageView.getInnerHeight());
    }*/

    public static void bindGif(final GifViewHolder gifViewHolder,
                               final ImageView imageView,
                               final AlbumItem albumItem) {
        ViewCompat.setTransitionName(imageView, albumItem.getPath());
        Context context = imageView.getContext();
        Glide.with(context)
                .asGif()
                .load(albumItem.getUri(context))
                .listener(new RequestListener<GifDrawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<GifDrawable> target, boolean isFirstResource) {
                        albumItem.error = true;
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(GifDrawable resource, Object model, Target<GifDrawable> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        resource.start();
                        gifViewHolder.setAttacher(imageView);
                        return false;
                    }
                })
                .apply(albumItem.getGlideRequestOptions(imageView.getContext()))
                .into(imageView);
    }
}
