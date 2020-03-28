package us.koller.cameraroll.adapter.album.viewHolder;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

import us.koller.cameraroll.R;
import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.util.animators.ColorFade;

public class GifViewHolder extends AlbumItemHolder {

    public GifViewHolder(View itemView) {
        super(itemView);
    }

    @Override
    int getIndicatorDrawableResource() {
        return R.drawable.gif_indicator;
    }

    @Override
    public void loadImage(final ImageView imageView, final AlbumItem albumItem) {
        ColorFade.animateToAlpha(0f, itemView);

        RequestOptions options = new RequestOptions()
                .error(R.drawable.error_placeholder)
                .signature(albumItem.getGlideSignature());

        Glide.with(imageView.getContext())
                .asGif()
                .load(albumItem.getPath())
                .transition(DrawableTransitionOptions.withCrossFade(crossFadeDuration))
                .listener(new RequestListener<GifDrawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                Target<GifDrawable> target, boolean isFirstResource) { return true;
                    }

                    @Override
                    public boolean onResourceReady(GifDrawable resource, Object model, Target<GifDrawable> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        resource.start();
                        return true;
                    }
                })
                .apply(options)
                .into(imageView);
    }
}
