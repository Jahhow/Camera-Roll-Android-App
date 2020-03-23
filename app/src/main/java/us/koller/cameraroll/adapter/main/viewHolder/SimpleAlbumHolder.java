package us.koller.cameraroll.adapter.main.viewHolder;

import android.view.View;
import android.widget.ImageView;

import us.koller.cameraroll.R;
import us.koller.cameraroll.data.models.Album;

public class SimpleAlbumHolder extends AlbumHolder {

    public SimpleAlbumHolder(View itemView) {
        super(itemView);
    }

    @Override
    public void setAlbum(Album album) {
        super.setAlbum(album);
        final ImageView image = itemView.findViewById(R.id.image);
        loadImage(image);
    }
}
