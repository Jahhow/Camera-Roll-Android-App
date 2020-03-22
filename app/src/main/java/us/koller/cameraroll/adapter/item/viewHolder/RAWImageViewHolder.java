package us.koller.cameraroll.adapter.item.viewHolder;

import com.davemorrissey.labs.subscaleview.DecoderFactory;
import com.davemorrissey.labs.subscaleview.ImageDecoder;
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder;

import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.imageDecoder.RAWImageRegionDecoder;

public class RAWImageViewHolder extends PhotoViewHolder {

    public RAWImageViewHolder(AlbumItem albumItem, int position) {
        super(albumItem, position);
    }

    @Override
    DecoderFactory<? extends ImageDecoder> getImageDecoderFactory() {
        return null; // force use RAWImageRegionDecoder (prevents from loading twice for the same RAW file)
    }

    @Override
    DecoderFactory<? extends ImageRegionDecoder> getImageRegionDecoderFactory() {
        return RAWImageRegionDecoder::new;
    }
}
