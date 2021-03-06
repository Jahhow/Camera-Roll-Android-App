package us.koller.cameraroll.adapter.main;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import us.koller.cameraroll.R;
import us.koller.cameraroll.adapter.AbstractRecyclerViewAdapter;
import us.koller.cameraroll.adapter.SelectorModeManager;
import us.koller.cameraroll.adapter.main.viewHolder.AlbumHolder;
import us.koller.cameraroll.adapter.main.viewHolder.NestedRecyclerViewAlbumHolder;
import us.koller.cameraroll.data.Settings;
import us.koller.cameraroll.data.models.Album;
import us.koller.cameraroll.styles.Style;
import us.koller.cameraroll.themes.Theme;
import us.koller.cameraroll.ui.AlbumActivity;
import us.koller.cameraroll.ui.MainActivity;
import us.koller.cameraroll.ui.ThemeableActivity;

public class MainAdapter extends AbstractRecyclerViewAdapter<ArrayList<Album>> {
    protected static final String TAG = MainAdapter.class.getSimpleName();
    private Style style;
    private RecyclerView.RecycledViewPool nestedRecyclerViewRecycledViewPool = null;

    public MainAdapter(Context context, boolean pick_photos) {
        super(pick_photos);

        Settings settings = Settings.getInstance(context);

        style = settings.getStyleInstance(context, pick_photos);

        setSelectorModeManager(new SelectorModeManager());
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder viewHolder;

        viewHolder = style.createViewHolderInstance(parent);
        if (viewHolder instanceof NestedRecyclerViewAlbumHolder) {
            NestedRecyclerViewAlbumHolder nestedHolder = (NestedRecyclerViewAlbumHolder) viewHolder;
            if (nestedRecyclerViewRecycledViewPool == null)
                nestedRecyclerViewRecycledViewPool = nestedHolder.nestedRecyclerView.getRecycledViewPool();
            else
                nestedHolder.nestedRecyclerView.setRecycledViewPool(nestedRecyclerViewRecycledViewPool);
            nestedHolder.setSelectorModeManager(getSelectorManager());
        }

        Context context = viewHolder.itemView.getContext();
        Theme theme = Settings.getInstance(context).getThemeInstance(context);
        ThemeableActivity.checkTags((ViewGroup) viewHolder.itemView, theme);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        final Album album = getData().get(position);
        AlbumHolder albumHolder = (AlbumHolder) holder;
        if (albumHolder.getAlbum() != null && albumHolder.getAlbum().equals(album))
            return;
        //Log.i(TAG, "bind album");
        albumHolder.setAlbum(album);
        holder.itemView.setOnClickListener(view -> {
            Intent intent = new Intent(view.getContext(), AlbumActivity.class);

            //intent.putExtra(AlbumActivity.ALBUM, album);
            intent.putExtra(AlbumActivity.ALBUM_PATH, album.getPath());

            if (pickPhotos()) {
                Context c = view.getContext();
                boolean allowMultiple = false;
                if (c instanceof Activity) {
                    Activity a = (Activity) c;
                    allowMultiple = a.getIntent()
                            .getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                }
                intent.setAction(MainActivity.PICK_PHOTOS);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
            } else {
                intent.setAction(AlbumActivity.VIEW_ALBUM);
            }

            ActivityOptionsCompat options;
            Activity activity = (Activity) view.getContext();
            if (pickPhotos()) {
                View toolbar = activity.findViewById(R.id.toolbar);
                options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        activity, toolbar, activity.getString(R.string.toolbar_transition_name));
                activity.startActivityForResult(intent,
                        MainActivity.PICK_PHOTOS_REQUEST_CODE, options.toBundle());
            } else {
                //todo
                //options = ActivityOptionsCompat.makeCustomAnimation(activity, R.anim.enter, R.anim.enter);
                //noinspection unchecked
                options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity);
                activity.startActivityForResult(intent,
                        MainActivity.REFRESH_PHOTOS_REQUEST_CODE, options.toBundle());
            }
        });
    }

    @Override
    public int getItemCount() {
        return getData() != null ? getData().size() : 0;
    }

    public boolean onBackPressed() {
        return getSelectorManager().onBackPressed();
    }

    @Override
    public void setSelectorModeManager(SelectorModeManager selectorManager) {
        super.setSelectorModeManager(selectorManager);
        notifyItemRangeChanged(0, getItemCount());
    }
}
