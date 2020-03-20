package us.koller.cameraroll.adapter.album.viewHolder;

import android.view.View;

import us.koller.cameraroll.R;

public class RAWImageHolder extends AlbumItemHolder {

    public RAWImageHolder(View itemView) {
        super(itemView);
    }

    @Override
    int getIndicatorDrawableResource() {
        return R.drawable.raw_indicator;
    }
}
