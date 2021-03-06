package us.koller.cameraroll.adapter.item.viewHolder;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.ui.ItemActivity;
import us.koller.cameraroll.util.ItemViewUtil;

public abstract class ViewHolder {

    View itemView;
    public AlbumItem albumItem;
    private int position;

    public ViewHolder(AlbumItem albumItem, int position) {
        this.albumItem = albumItem;
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    View inflatePhotoView(ViewGroup container) {
        View v = ItemViewUtil.inflatePhotoView(container);
        v.setTag(albumItem.getPath());
        itemView = v;
        return v;
    }

    ViewGroup inflateVideoView(ViewGroup container) {
        ViewGroup v = ItemViewUtil.inflateVideoView(container);
        v.setTag(albumItem.getPath());
        itemView = v;
        return v;
    }

    void imageOnClick(@NonNull View view) {
        try {
            ((ItemActivity) view.getContext()).imageOnClick();
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
    }

    public View getView(ViewGroup container) {
        if (itemView == null) {
            itemView = inflateView(container);
        }
        return itemView;
    }

    public abstract View inflateView(ViewGroup container);

    public void onDestroy() {
        this.itemView.setOnClickListener(null);
        this.itemView = null;
        this.albumItem = null;
    }

    public String getTag() {
        return albumItem.getPath();
    }

    //called when the viewHolder is shown after shared element transition
    public abstract void onShowViewHolder();
}
