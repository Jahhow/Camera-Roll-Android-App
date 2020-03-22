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

public class RAWImageRegionDecoder implements ImageRegionDecoder {
    static final String TAG = RAWImageRegionDecoder.class.getSimpleName();
    Bitmap bitmap = null;

    @Override
    @NonNull
    public Point init(Context context, @NonNull Uri uri) throws Exception {
        bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));
        return new Point(bitmap.getWidth(), bitmap.getHeight());
    }

    @Override
    @NonNull
    public Bitmap decodeRegion(Rect rect, int sampleSize) {
        float scale = 1f / sampleSize;
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height(), matrix, false);
    }

    @Override
    public boolean isReady() {
        return bitmap != null;
    }

    @Override
    public void recycle() {
        bitmap.recycle();
        bitmap = null;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }
}
