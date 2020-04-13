package us.koller.cameraroll.imageDecoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.Rotate;
import com.bumptech.glide.request.RequestOptions;
import com.davemorrissey.labs.subscaleview.ImageDecoder;

import java.util.concurrent.ExecutionException;

public class GlideImageDecoder implements ImageDecoder {
    protected static final String TAG = GlideImageDecoder.class.getSimpleName();

    @Override
    public Bitmap decode(@NonNull Context context, @NonNull Uri uri, int orientationAngle) {
        //Log.i(TAG, "decode");
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int px = Math.min(displayMetrics.heightPixels, displayMetrics.widthPixels) >> 1;
        try {
            return Glide.with(context)
                    .asBitmap()
                    .fitCenter()
                    .load(uri)
                    .apply(new RequestOptions().transform(new Rotate(-orientationAngle)))
                    .submit(px, px)
                    .get();
        } catch (ExecutionException | InterruptedException ignored) {
        }
        //Log.i(TAG, "error");
        return null;
    }
}