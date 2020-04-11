package us.koller.cameraroll.adapter.album;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.michaelflisar.dragselectrecyclerview.DragSelectTouchListener;

import us.koller.cameraroll.R;
import us.koller.cameraroll.adapter.AbstractRecyclerViewAdapter;
import us.koller.cameraroll.adapter.SelectorModeManager;
import us.koller.cameraroll.adapter.album.viewHolder.AlbumItemHolder;
import us.koller.cameraroll.adapter.album.viewHolder.GifViewHolder;
import us.koller.cameraroll.adapter.album.viewHolder.PhotoViewHolder;
import us.koller.cameraroll.adapter.album.viewHolder.RAWImageHolder;
import us.koller.cameraroll.adapter.album.viewHolder.VideoViewHolder;
import us.koller.cameraroll.data.models.Album;
import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.data.models.Gif;
import us.koller.cameraroll.data.models.Photo;
import us.koller.cameraroll.data.models.RAWImage;
import us.koller.cameraroll.data.models.Video;
import us.koller.cameraroll.ui.ItemActivity;

public class AlbumAdapter extends AbstractRecyclerViewAdapter<Album> {
    protected static final String TAG = AlbumAdapter.class.getSimpleName();

    @SuppressWarnings("FieldCanBeLocal")
    private final int VIEW_TYPE_PHOTO = 1;
    private final int VIEW_TYPE_GIF = 2;
    private final int VIEW_TYPE_VIDEO = 3;
    private final int VIEW_TYPE_RAW = 4;
    private final RecyclerView recyclerView;
    //static int i = 0;

    private DragSelectTouchListener dragSelectTouchListener;

    public AlbumAdapter(SelectorModeManager.Callback callback, final RecyclerView recyclerView,
                        final Album album, boolean pick_photos) {
        this(callback, recyclerView, album, pick_photos, new SelectorModeManager());
    }

    public AlbumAdapter(SelectorModeManager.Callback callback, final RecyclerView recyclerView,
                        final Album album, boolean pick_photos, SelectorModeManager selectorModeManager) {
        super(pick_photos);
        this.recyclerView = recyclerView;

        setData(album);
        setSelectorModeManager(selectorModeManager);
        if (pick_photos) {
            getSelectorManager().setSelectorMode(true);
        }
        if (callback != null) {
            getSelectorManager().addCallback(callback);
        }

        //((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        /*recyclerView.setItemAnimator(new DefaultItemAnimator() {
            @Override
            public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromX, int fromY, int toX, int toY) {
                if (oldHolder != newHolder)
                    //Log.w(TAG, "animateChange");
                return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY);
            }
        });*/

        if (callback != null && dragSelectEnabled()) {
            dragSelectTouchListener = new DragSelectTouchListener()
                    .withSelectListener(new DragSelectTouchListener.OnDragSelectListener() {
                        @Override
                        public void onSelectChange(int start, int end, boolean isSelected) {
                            for (int i = start; i <= end; i++) {
                                AlbumItem albumItem = getData().getAlbumItems().get(i);
                                boolean selected = getSelectorManager().onToggleItemSelection(albumItem.getPath());
                                //update ViewHolder
                                for (int j = 0; j < recyclerView.getChildCount(); ++j) {
                                    AlbumItemHolder albumItemHolder = (AlbumItemHolder) recyclerView.getChildViewHolder(recyclerView.getChildAt(j));
                                    if (albumItemHolder.getAlbumItem() == albumItem) {
                                        albumItemHolder.setSelected(selected);
                                        break;
                                    }
                                }
                            }
                        }
                    });
            recyclerView.addOnItemTouchListener(dragSelectTouchListener);
        }
    }

    @Override
    public int getItemViewType(int position) {
        AlbumItem albumItem = getData().getAlbumItems().get(position);
        if (albumItem instanceof RAWImage) {
            return VIEW_TYPE_RAW;
        } else if (albumItem instanceof Gif) {
            return VIEW_TYPE_GIF;
        } else if (albumItem instanceof Photo) {
            return VIEW_TYPE_PHOTO;
        } else if (albumItem instanceof Video) {
            return VIEW_TYPE_VIDEO;
        }
        return -1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.albumitem_cover, parent, false);
        switch (viewType) {
            case VIEW_TYPE_RAW:
                return new RAWImageHolder(v);
            case VIEW_TYPE_GIF:
                return new GifViewHolder(v);
            case VIEW_TYPE_VIDEO:
                return new VideoViewHolder(v);
            default:
                return new PhotoViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        AlbumItemHolder albumItemHolder = (AlbumItemHolder) holder;
        View itemView = albumItemHolder.itemView;
        AlbumItem albumItem = getData().getAlbumItems().get(position);

        //Log.i(TAG, "onBindViewHolder " + position);
        boolean selected = getSelectorManager().isItemSelected(albumItem.getPath());
        albumItemHolder.setSelected(selected, false);

        if (!albumItem.equals(albumItemHolder.getAlbumItem())) {
            albumItemHolder.setAlbumItem(albumItem);
            itemView.setTag(albumItem.getPath());
            itemView.setOnClickListener(view -> {
                if (getSelectorMode()) {
                    onToggleItemSelection(albumItemHolder);
                } else {
                    //Log.d(TAG, "onClick: " + getData().getPath());
                /*int[] a = new int[2];
                itemView.getLocationOnScreen(a);
                albumItem.itemViewBound = new Rect(a[0], a[1], a[0] + itemView.getWidth(), a[1] + itemView.getHeight());*/
                    Context context = itemView.getContext();
                    Intent intent = new Intent(context, ItemActivity.class);
                    intent.putExtra(ItemActivity.ALBUM_ITEM, albumItem);
                    intent.putExtra(ItemActivity.ALBUM_PATH, getData().getPath());
                    intent.putExtra(ItemActivity.ITEM_POSITION, getData().getAlbumItems().indexOf(albumItem));
                    ((Activity) context).startActivityForResult(intent, ItemActivity.VIEW_IMAGE);
                }
            });
            itemView.setOnLongClickListener(view -> {
                if (!getSelectorMode()) {
                    notifySelectorModeChange(true);
                    clearSelectedItemsList();
                }

                onToggleItemSelection(albumItemHolder);

                if (dragSelectEnabled()) {
                    //notify DragSelectTouchListener
                    int position1 = getData().getAlbumItems().indexOf(albumItem);
                    dragSelectTouchListener.startDragSelection(position1);
                }
                return true;
            });
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        AlbumItemHolder albumItemHolder = (AlbumItemHolder) holder;
        if (!getSelectorMode())
            albumItemHolder.setSelected(false, false);
    }

    public boolean isSelectorModeActive() {
        return getSelectorMode() && !pickPhotos();
    }

    public void restoreSelectedItems() {
        //notify AlbumActivity
        getSelectorManager().onSelectorModeEnter();

        for (int i = 0; i < getData().getAlbumItems().size(); i++) {
            if (getSelectorManager().isItemSelected(getData().getAlbumItems().get(i).getPath())) {
                notifyItemChanged(i);
            }
        }

        getSelectorManager().onItemSelected(getSelectorManager().getSelectedItemCount());
    }

    private int getSelectedItemCount() {
        return getSelectorManager().getSelectedItemCount();
    }

    private void onToggleItemSelection(AlbumItemHolder holder) {
        boolean selected = getSelectorManager().onToggleItemSelection(holder.albumItem.getPath());
        holder.setSelected(selected);
        if (getSelectedItemCount() == 0 && !pickPhotos()) {
            exitSelectorMode(false);
        }
    }

    public String[] exitSelectorMode(boolean getSelectedPath) {
        return exitSelectorMode(getSelectedPath, true);
    }

    public String[] exitSelectorMode(boolean getSelectedPath, boolean clearUI) {
        notifySelectorModeChange(false);

        if (clearUI) {
            for (int i = 0; i < recyclerView.getChildCount(); ++i) {
                AlbumItemHolder albumItemHolder = (AlbumItemHolder) recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
                albumItemHolder.setSelected(false);
            }
        }

        String[] paths;
        if (getSelectedPath) {
            paths = getSelectorManager().createStringArray();
        } else {
            paths = null;
        }

        clearSelectedItemsList();
        return paths;
    }

    public boolean onBackPressed() {
        if (getSelectorMode() && !pickPhotos()) {
            exitSelectorMode(false);
            return true;
        }
        return false;
    }

    private boolean getSelectorMode() {
        return getSelectorManager().isSelectorModeActive();
    }

    protected void notifySelectorModeChange(boolean activate) {
        getSelectorManager().setSelectorMode(activate);
    }

    public boolean dragSelectEnabled() {
        return true;
    }

    protected void clearSelectedItemsList() {
        getSelectorManager().clearList();
    }

    @Override
    public int getItemCount() {
        return getData() != null ? getData().getAlbumItems().size() : 0;
    }
}