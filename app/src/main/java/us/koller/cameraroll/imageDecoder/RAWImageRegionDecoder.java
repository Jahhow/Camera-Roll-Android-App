package us.koller.cameraroll.imageDecoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.davemorrissey.labs.subscaleview.ImageRegionDecoder;

import java.io.IOException;

public class RAWImageRegionDecoder implements ImageRegionDecoder {
    private static final String TAG = RAWImageRegionDecoder.class.getSimpleName();
    private Context context;
    private Uri uri;
    private final Object bitmapLock = new Object();
    private Bitmap bitmap = null;

    @Override
    @NonNull
    public Point init(Context context, @NonNull Uri uri) throws Exception {
        this.context = context;
        this.uri = uri;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri), null, options);
        //Log.i(TAG, "dimen " + options.outWidth + ", " + options.outHeight);
        return new Point(options.outWidth, options.outHeight);
    }

    @Override
    @NonNull
    public Bitmap decodeRegion(@NonNull Rect rect, int sampleSize) throws IOException {
        //Log.i(TAG, "decodeRegion");
        synchronized (bitmapLock) {
            if (bitmap == null) {
                bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));
            }
        }
        float scale = 1f / sampleSize;
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height(), matrix, false);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void recycle() {
        synchronized (bitmapLock) {
            bitmap.recycle();
            bitmap = null;
        }
    }
}
