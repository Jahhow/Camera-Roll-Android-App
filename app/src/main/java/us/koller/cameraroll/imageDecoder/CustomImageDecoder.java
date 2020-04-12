package us.koller.cameraroll.imageDecoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.davemorrissey.labs.subscaleview.ImageDecoder;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class CustomImageDecoder implements ImageDecoder {
    @Override
    public Bitmap decode(@NonNull Context context, @NonNull Uri uri, int orientationAngle) throws FileNotFoundException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        return BitmapFactory.decodeStream(inputStream);
    }
}