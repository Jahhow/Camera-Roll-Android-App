package us.koller.cameraroll.imageDecoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.davemorrissey.labs.subscaleview.ImageDecoder;

import us.koller.cameraroll.data.Settings;

public class CustomImageDecoder implements ImageDecoder {

    @Override
    @NonNull
    public Bitmap decode(@NonNull Context context, @NonNull Uri uri) throws Exception {
        boolean use8BitColor = Settings.getInstance(context).use8BitColor();

        RequestOptions options = new RequestOptions()
                .format(use8BitColor ? DecodeFormat.PREFER_ARGB_8888 : DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .fitCenter();

        FutureTarget<Bitmap> builder = Glide.with(context)
                .asBitmap()
                .load(uri)
                .apply(options)
                .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);

        return builder.get();
    }
}
