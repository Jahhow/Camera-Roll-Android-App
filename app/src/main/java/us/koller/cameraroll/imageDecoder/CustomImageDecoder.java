package us.koller.cameraroll.imageDecoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.davemorrissey.labs.subscaleview.ImageDecoder;

import java.io.InputStream;

import us.koller.cameraroll.data.Settings;

public class CustomImageDecoder implements ImageDecoder {
    @Override
    public Bitmap decode(@NonNull Context context, @NonNull Uri uri) throws Exception {
        boolean use8BitColor = Settings.getInstance(context).use8BitColor();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = use8BitColor ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;

        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        return BitmapFactory.decodeStream(inputStream, null, options);
    }
}