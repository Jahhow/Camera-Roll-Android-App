package us.koller.cameraroll.adapter.album.viewHolder;

import android.view.View;

import us.koller.cameraroll.R;

public class VideoViewHolder extends AlbumItemHolder {

    public VideoViewHolder(View itemView) {
        super(itemView);
    }

    @Override
    int getIndicatorDrawableResource() {
        return R.drawable.video_indicator;
    }
}
