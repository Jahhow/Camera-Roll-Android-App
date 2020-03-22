package us.koller.cameraroll.adapter.item.viewHolder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import uk.co.senab.photoview.PhotoViewAttacher;
import us.koller.cameraroll.R;
import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.util.ItemViewUtil;

public class GifViewHolder extends ViewHolder {

    private PhotoViewAttacher attacher;

    public GifViewHolder(AlbumItem albumItem, int position) {
        super(albumItem, position);
    }

    @Override
    public View inflateView(ViewGroup container) {
        View view = ItemViewUtil.inflateGifView(container);
        ImageView imageView = view.findViewById(R.id.image);
        ItemViewUtil.bindTransitionView(imageView, albumItem);
        return view;
    }

    private void reloadGif() {
        ImageView view = itemView.findViewById(R.id.image);
        ItemViewUtil.bindGif(this, view, albumItem);
    }

    public void setAttacher(ImageView imageView) {
        attacher = new PhotoViewAttacher(imageView);
        attacher.setOnViewTapListener(new PhotoViewAttacher.OnViewTapListener() {
            @Override
            public void onViewTap(View view, float x, float y) {
                imageOnClick(view);
            }
        });
    }

    @Override
    public void onShowViewHolder() {
        reloadGif();
    }

    @Override
    public void onDestroy() {
        if (attacher != null) {
            attacher.cleanup();
            attacher = null;
        }
        super.onDestroy();
    }
}
