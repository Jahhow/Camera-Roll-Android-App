package us.koller.cameraroll.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import us.koller.cameraroll.R;
import us.koller.cameraroll.adapter.SelectorModeManager;
import us.koller.cameraroll.adapter.album.AlbumAdapter;
import us.koller.cameraroll.adapter.album.viewHolder.AlbumItemHolder;
import us.koller.cameraroll.data.Settings;
import us.koller.cameraroll.data.fileOperations.FileOperation;
import us.koller.cameraroll.data.fileOperations.Move;
import us.koller.cameraroll.data.fileOperations.Rename;
import us.koller.cameraroll.data.models.Album;
import us.koller.cameraroll.data.models.AlbumItem;
import us.koller.cameraroll.data.models.File_POJO;
import us.koller.cameraroll.data.models.VirtualAlbum;
import us.koller.cameraroll.data.provider.MediaProvider;
import us.koller.cameraroll.data.provider.Provider;
import us.koller.cameraroll.themes.Theme;
import us.koller.cameraroll.ui.widget.FastScrollerRecyclerView;
import us.koller.cameraroll.ui.widget.GridMarginDecoration;
import us.koller.cameraroll.ui.widget.SwipeBackCoordinatorLayout;
import us.koller.cameraroll.util.MediaType;
import us.koller.cameraroll.util.SortUtil;
import us.koller.cameraroll.util.StorageUtil;
import us.koller.cameraroll.util.Util;
import us.koller.cameraroll.util.animators.ColorFade;

public class AlbumActivity extends ThemeableActivity
        implements SwipeBackCoordinatorLayout.OnSwipeListener, SelectorModeManager.Callback {
    protected static final String TAG = AlbumActivity.class.getSimpleName();

    public static final int FILE_OP_DIALOG_REQUEST = 1;

    //public static final String ALBUM = "ALBUM";
    public static final String ALBUM_PATH = "ALBUM_PATH";
    public static final String VIEW_ALBUM = "VIEW_ALBUM";
    public static final String ALBUM_ITEM_REMOVED = "ALBUM_ITEM_REMOVED";
    public static final String ALBUM_ITEM_RENAMED = "ALBUM_ITEM_RENAMED";
    public static final String EXTRA_CURRENT_ALBUM_POSITION = "EXTRA_CURRENT_ALBUM_POSITION";
    public static final String RECYCLER_VIEW_SCROLL_STATE = "RECYCLER_VIEW_STATE";

    //private int sharedElementReturnPosition = -1;

    /*private final SharedElementCallback mCallback = new SharedElementCallback() {
        @Override
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
            if (sharedElementReturnPosition != -1 && album != null &&
                    sharedElementReturnPosition < album.getAlbumItems().size()) {
                String newTransitionName = album.getAlbumItems().get(sharedElementReturnPosition).getPath();
                View layout = recyclerView.findViewWithTag(newTransitionName);
                View newSharedElement = layout != null ? layout.findViewById(R.id.image) : null;
                if (newSharedElement != null) {
                    names.clear();
                    names.add(newTransitionName);
                    sharedElements.clear();
                    sharedElements.put(newTransitionName, newSharedElement);
                }
                sharedElementReturnPosition = -1;
            } else {
                View navigationBar = findViewById(android.R.id.navigationBarBackground);
                View statusBar = findViewById(android.R.id.statusBarBackground);
                if (navigationBar != null) {
                    names.add(navigationBar.getTransitionName());
                    sharedElements.put(navigationBar.getTransitionName(), navigationBar);
                }
                if (statusBar != null) {
                    names.add(statusBar.getTransitionName());
                    sharedElements.put(statusBar.getTransitionName(), statusBar);
                }
            }
        }
    };*/

    private Album album;
    private RecyclerView recyclerView;
    private AlbumAdapter recyclerViewAdapter;
    private Snackbar snackbar;
    private Menu menu;
    private boolean pick_photos;
    private boolean allowMultiple;
    int selectedItemCount = 0;

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setEnterTransition(new TransitionSet().addTransition(new Fade()).addTransition(new ChangeTransform()));
        }*/
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);

        pick_photos = getIntent().getAction() != null
                && getIntent().getAction().equals(MainActivity.PICK_PHOTOS);
        allowMultiple = getIntent().getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);

        MediaProvider.checkPermission(this);

        //setExitSharedElementCallback(mCallback);

        final ViewGroup swipeBackView = findViewById(R.id.swipeBackView);
        if (swipeBackView instanceof SwipeBackCoordinatorLayout) {
            ((SwipeBackCoordinatorLayout) swipeBackView).setOnSwipeListener(this);
        }

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (!pick_photos) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AnimatedVectorDrawable drawable = (AnimatedVectorDrawable)
                        ContextCompat.getDrawable(AlbumActivity.this, R.drawable.back_to_cancel_avd);
                //mutating avd to reset it
                drawable.mutate();
                toolbar.setNavigationIcon(drawable);
            } else {
                toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white);
            }
            Drawable navIcon = toolbar.getNavigationIcon();
            if (navIcon != null) {
                navIcon = DrawableCompat.wrap(navIcon);
                DrawableCompat.setTint(navIcon.mutate(), textColorSecondary);
                toolbar.setNavigationIcon(navIcon);
            }
        } else {
            if (actionBar != null) {
                actionBar.setTitle(allowMultiple ? getString(R.string.pick_photos) :
                        getString(R.string.pick_photo));
            }
            toolbar.setNavigationIcon(R.drawable.ic_clear_white);
            Drawable navIcon = toolbar.getNavigationIcon();
            if (navIcon != null) {
                navIcon = DrawableCompat.wrap(navIcon);
                DrawableCompat.setTint(navIcon.mutate(), accentTextColor);
                toolbar.setNavigationIcon(navIcon);
            }
            Util.setDarkStatusBarIcons(findViewById(R.id.root_view));
            Util.colorToolbarOverflowMenuIcon(toolbar, accentTextColor);
        }

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (recyclerViewAdapter != null && recyclerViewAdapter.isSelectorModeActive()) {
                    recyclerViewAdapter.exitSelectorMode(false);
                } else {
                    onBackPressed();
                }
            }
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setItemViewCacheSize(40);
        final int columnCount = Settings.getInstance(this).getColumnCount(this);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, columnCount);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerViewAdapter = new AlbumAdapter(this, recyclerView, album, pick_photos);
        recyclerView.setAdapter(recyclerViewAdapter);
        float albumGridSpacing = getResources().getDimension(R.dimen.album_grid_spacing);
        ((FastScrollerRecyclerView) recyclerView).addOuterGridSpacing((int) (albumGridSpacing / 2));
        recyclerView.addItemDecoration(new GridMarginDecoration((int) albumGridSpacing));
        if (savedInstanceState != null
                && savedInstanceState.containsKey(RECYCLER_VIEW_SCROLL_STATE)) {
            recyclerView.getLayoutManager().onRestoreInstanceState(
                    savedInstanceState.getParcelable(RECYCLER_VIEW_SCROLL_STATE));
        }

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            //private float scrollY = 0.0f;

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (recyclerViewAdapter.isSelectorModeActive()
                        || pick_photos) {
                    return;
                }

                float translationY = toolbar.getTranslationY() - dy;
                if (-translationY > toolbar.getHeight()) {
                    translationY = -toolbar.getHeight();
                    if (theme.elevatedToolbar()) {
                        toolbar.setActivated(true);
                    }
                } else if (translationY > 0) {
                    translationY = 0;
                    if (theme.elevatedToolbar() &&
                            !recyclerView.canScrollVertically(-1)) {
                        toolbar.setActivated(false);
                    }
                }
                toolbar.setTranslationY(translationY);

                //animate statusBarIcon color
                if (theme.darkStatusBarIcons()) {
                    float animatedValue = (-translationY) / toolbar.getHeight();
                    if (animatedValue > 0.9f) {
                        Util.setLightStatusBarIcons(findViewById(R.id.root_view));
                    } else {
                        Util.setDarkStatusBarIcons(findViewById(R.id.root_view));
                    }
                }
            }
        });

        final FloatingActionButton fab = findViewById(R.id.fab);
        if (!pick_photos) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Drawable d = ContextCompat.getDrawable(this,
                        /*R.drawable.ic_delete_avd*/ R.drawable.ic_share_avd);
                fab.setImageDrawable(d);
            } else {
                fab.setImageResource(/*R.drawable.ic_delete_white*/ R.drawable.ic_share_white);
            }
        } else {
            fab.setImageResource(R.drawable.ic_send_white);
        }
        Drawable d = fab.getDrawable();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            d.setTint(accentTextColor);
        } else {
            d = DrawableCompat.wrap(d);
            DrawableCompat.setTint(d.mutate(), accentTextColor);
        }
        fab.setImageDrawable(d);
        fab.setScaleX(0.0f);
        fab.setScaleY(0.0f);

        final ViewGroup rootView = findViewById(R.id.root_view);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            rootView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                @RequiresApi(api = Build.VERSION_CODES.KITKAT_WATCH)
                public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                    toolbar.setPadding(toolbar.getPaddingStart(),
                            toolbar.getPaddingTop() + insets.getSystemWindowInsetTop(),
                            toolbar.getPaddingEnd(),
                            toolbar.getPaddingBottom());

                    ViewGroup.MarginLayoutParams toolbarParams
                            = (ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
                    toolbarParams.leftMargin += insets.getSystemWindowInsetLeft();
                    toolbarParams.rightMargin += insets.getSystemWindowInsetRight();
                    toolbar.setLayoutParams(toolbarParams);

                    recyclerView.setPadding(recyclerView.getPaddingStart() + insets.getSystemWindowInsetLeft(),
                            recyclerView.getPaddingTop() + insets.getSystemWindowInsetTop(),
                            recyclerView.getPaddingEnd() + insets.getSystemWindowInsetRight(),
                            recyclerView.getPaddingBottom() + insets.getSystemWindowInsetBottom());

                    fab.setTranslationY(-insets.getSystemWindowInsetBottom());
                    fab.setTranslationX(-insets.getSystemWindowInsetRight());

                    rootView.setOnApplyWindowInsetsListener(null);
                    return insets.consumeSystemWindowInsets();
                }
            });
        } else {
            rootView.getViewTreeObserver()
                    .addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    // hacky way of getting window insets on pre-Lollipop
                                    // somewhat works...
                                    int[] screenSize = Util.getScreenSize(AlbumActivity.this);

                                    int[] windowInsets = new int[]{
                                            Math.abs(screenSize[0] - rootView.getLeft()),
                                            Math.abs(screenSize[1] - rootView.getTop()),
                                            Math.abs(screenSize[2] - rootView.getRight()),
                                            Math.abs(screenSize[3] - rootView.getBottom())};

                                    toolbar.setPadding(toolbar.getPaddingStart(),
                                            toolbar.getPaddingTop() + windowInsets[1],
                                            toolbar.getPaddingEnd(),
                                            toolbar.getPaddingBottom());

                                    ViewGroup.MarginLayoutParams toolbarParams
                                            = (ViewGroup.MarginLayoutParams) toolbar.getLayoutParams();
                                    toolbarParams.leftMargin += windowInsets[0];
                                    toolbarParams.rightMargin += windowInsets[2];
                                    toolbar.setLayoutParams(toolbarParams);

                                    recyclerView.setPadding(recyclerView.getPaddingStart() + windowInsets[0],
                                            recyclerView.getPaddingTop() + windowInsets[1],
                                            recyclerView.getPaddingEnd() + windowInsets[2],
                                            recyclerView.getPaddingBottom() + windowInsets[3]);
                                    recyclerView.scrollToPosition(0);

                                    fab.setTranslationX(-windowInsets[2]);
                                    fab.setTranslationY(-windowInsets[3]);

                                    rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                }
                            });
        }

        //onNewIntent(getIntent());

        //needed for transparent statusBar
        setSystemUiFlags();

        //load album
        String path;
        if (savedInstanceState != null && savedInstanceState.containsKey(ALBUM_PATH)) {
            path = savedInstanceState.getString(ALBUM_PATH);
        } else {
            path = getIntent().getStringExtra(ALBUM_PATH);
        }
        MediaProvider.getAlbum(this, path,
                new MediaProvider.OnAlbumLoadedCallback() {
                    @Override
                    public void onAlbumLoaded(Album album) {
                        AlbumActivity.this.album = album;
                        AlbumActivity.this.onAlbumLoaded(savedInstanceState);
                    }
                });
    }

    private void onAlbumLoaded(Bundle savedInstanceState) {
        int sort_by = Settings.getInstance(this).sortAlbumBy();
        SortUtil.sort(album.getAlbumItems(), sort_by);

        ActionBar actionBar = getSupportActionBar();
        if (!pick_photos && actionBar != null) {
            actionBar.setTitle(album.getName());
        }

        recyclerViewAdapter.setData(album);

        //restore Selector mode, when needed
        if (savedInstanceState != null) {
            final SelectorModeManager manager = new SelectorModeManager(savedInstanceState);
            manager.addCallback(this);
            recyclerViewAdapter.setSelectorModeManager(manager);
            final View rootView = findViewById(R.id.root_view);
            rootView.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (manager.isSelectorModeActive()) {
                                recyclerViewAdapter.restoreSelectedItems();
                            }
                        }
                    });

        }

        if (!pick_photos && menu != null) {
            setupMenu();
        }
    }

    private void setupMenu() {
        if (album instanceof VirtualAlbum) {
            menu.findItem(R.id.exclude).setVisible(false);
            menu.findItem(R.id.rename).setVisible(false);
        } else {
            //setup exclude checkbox
            boolean enabled = !Provider
                    .isDirExcludedBecauseParentDirIsExcluded(album.getPath(),
                            Provider.getExcludedPaths());
            menu.findItem(R.id.exclude).setEnabled(enabled);
            menu.findItem(R.id.exclude).setChecked(album.excluded || !enabled);
        }

        menu.findItem(R.id.pin).setChecked(album.pinned);

        if (recyclerViewAdapter.isSelectorModeActive()) {
            handleMenuVisibilityForSelectorMode(true);
        }
    }

    /*@Override
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onActivityReenter(int requestCode, Intent data) {
        super.onActivityReenter(requestCode, data);
        //Log.d("AlbumActivity", "onActivityReenter: " + this);
        if (data != null) {
            sharedElementReturnPosition = data.getIntExtra(EXTRA_CURRENT_ALBUM_POSITION, -1);
            if (sharedElementReturnPosition > -1 && album != null
                    && sharedElementReturnPosition < album.getAlbumItems().size()) {
                AlbumItem albumItem = album.getAlbumItems().get(sharedElementReturnPosition);
                albumItem.isSharedElement = true;
                postponeEnterTransition();
                recyclerView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int l, int t, int r, int b,
                                               int oL, int oT, int oR, int oB) {
                        recyclerView.removeOnLayoutChangeListener(this);
                        startPostponedEnterTransition();
                    }
                });
                recyclerView.scrollToPosition(sharedElementReturnPosition);
            }
        }
    }*/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.album, menu);
        this.menu = menu;

        if (pick_photos) {
            menu.findItem(R.id.share).setVisible(false);
            menu.findItem(R.id.exclude).setVisible(false);
            menu.findItem(R.id.pin).setVisible(false);
            menu.findItem(R.id.rename).setVisible(false);
            menu.findItem(R.id.copy).setVisible(false);
            menu.findItem(R.id.move).setVisible(false);
            menu.findItem(R.id.delete).setVisible(false);
        } else if (album != null) {
            setupMenu();
        }

        int sort_by = Settings.getInstance(this).sortAlbumBy();
        if (sort_by == SortUtil.BY_DATE) {
            menu.findItem(R.id.sort_by_date).setChecked(true);
        } else if (sort_by == SortUtil.BY_NAME) {
            menu.findItem(R.id.sort_by_name).setChecked(true);
        }

        MenuItem selectAll = menu.findItem(R.id.select_all);
        Drawable drawable = DrawableCompat.wrap(selectAll.getIcon());
        DrawableCompat.setTint(drawable, accentTextColor);
        selectAll.setIcon(DrawableCompat.unwrap(drawable));

        return super.onCreateOptionsMenu(menu);
    }

    public void handleMenuVisibilityForSelectorMode(boolean selectorModeActive) {
        if (menu != null) {
            menu.findItem(R.id.exclude).setVisible(!selectorModeActive);
            menu.findItem(R.id.pin).setVisible(!selectorModeActive);
            menu.findItem(R.id.rename).setVisible(!selectorModeActive);
            menu.findItem(R.id.sort_by).setVisible(!selectorModeActive);
            //show share button
            menu.findItem(R.id.share).setVisible(selectorModeActive);
            //show copy & move button
            menu.findItem(R.id.copy).setVisible(selectorModeActive);
            menu.findItem(R.id.move).setVisible(selectorModeActive);
            menu.findItem(R.id.select_all).setVisible(selectorModeActive);
            menu.findItem(R.id.delete).setVisible(selectorModeActive);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final String[] selected_items_paths;
        Intent intent;
        switch (item.getItemId()) {
            case R.id.select_all:
                SelectorModeManager manager = recyclerViewAdapter.getSelectorManager();
                String[] paths = new String[album.getAlbumItems().size()];
                for (int i = 0; i < album.getAlbumItems().size(); i++) {
                    paths[i] = album.getAlbumItems().get(i).getPath();
                }
                manager.selectAll(paths);
                for (int i = 0; i < recyclerView.getChildCount(); ++i) {
                    AlbumItemHolder albumItemHolder = (AlbumItemHolder) recyclerView.getChildViewHolder(recyclerView.getChildAt(i));
                    albumItemHolder.setSelected(true);
                }
                break;
            case R.id.share:
                //share multiple items
                shareSelectedItems();
                break;
            case R.id.copy:
            case R.id.move:
                selected_items_paths = recyclerViewAdapter.exitSelectorMode(true);
                intent = new Intent(this, FileOperationDialogActivity.class);
                intent.setAction(item.getItemId() == R.id.copy ?
                        FileOperationDialogActivity.ACTION_COPY :
                        FileOperationDialogActivity.ACTION_MOVE);
                intent.putExtra(FileOperationDialogActivity.FILES, selected_items_paths);
                //noinspection unchecked
                startActivityForResult(intent, FILE_OP_DIALOG_REQUEST,
                        ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle());
                break;
            case R.id.delete:
                deleteSelectedItems();
                break;
            case R.id.exclude:
                Provider.loadExcludedPaths(this);
                if (!album.excluded) {
                    Provider.addExcludedPath(this, album.getPath());
                    album.excluded = true;
                } else {
                    Provider.removeExcludedPath(this, album.getPath());
                    album.excluded = false;
                }
                item.setChecked(album.excluded);
                break;
            case R.id.pin:
                Provider.loadPinnedPaths(this);
                if (!album.pinned) {
                    Provider.pinPath(this, album.getPath());
                    album.pinned = true;
                } else {
                    Provider.unpinPath(this, album.getPath());
                    album.pinned = false;
                }
                item.setChecked(album.pinned);
                break;
            case R.id.rename:
                File_POJO file = new File_POJO(album.getPath(), false).setName(album.getName());
                Rename.Util.getRenameDialog(this, file, new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {

                    }
                }).show();
                break;
            case R.id.sort_by_date:
            case R.id.sort_by_name:
                item.setChecked(true);

                int sort_by = item.getItemId() == R.id.sort_by_date ?
                        SortUtil.BY_DATE : SortUtil.BY_NAME;
                Settings.getInstance(this).sortAlbumBy(this, sort_by);

                SortUtil.sort(album.getAlbumItems(), sort_by);

                recyclerViewAdapter.notifyDataSetChanged();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null && data.getAction() != null) {
            onNewIntent(data);
        }
    }

    @Override
    public void onPermissionGranted() {
        super.onPermissionGranted();
        this.supportFinishAfterTransition();
    }

    public void deleteAlbumItemsSnackbar(String[] selected_items) {
        if (!MediaProvider.checkPermission(this)) {
            return;
        }

        final int[] indices = new int[selected_items.length];
        final AlbumItem[] deletedItems = new AlbumItem[selected_items.length];
        for (int i = selected_items.length - 1; i >= 0; i--) {
            for (int k = 0; k < album.getAlbumItems().size(); k++) {
                AlbumItem albumItem = album.getAlbumItems().get(k);
                if (selected_items[i].equals(albumItem.getPath())) {
                    indices[i] = k;
                    deletedItems[i] = albumItem;
                    album.getAlbumItems().remove(k);
                    recyclerViewAdapter.notifyItemRemoved(k);
                }
            }
        }

        int messageRes = selected_items.length == 1 ? R.string.file_deleted : R.string.files_deleted;
        String message = getString(messageRes, selected_items.length);

        //noinspection deprecation
        snackbar = Snackbar.make(findViewById(R.id.root_view), message, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        for (int i = 0; i < deletedItems.length; i++) {
                            AlbumItem albumItem = deletedItems[i];
                            int index = indices[i];
                            album.getAlbumItems().add(index, albumItem);
                            recyclerViewAdapter.notifyItemInserted(index);
                        }
                    }
                })
                .setCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar snackbar, int event) {
                        super.onDismissed(snackbar, event);
                        if (event != Snackbar.Callback.DISMISS_EVENT_ACTION) {
                            deleteAlbumItems(deletedItems, indices);
                        }
                    }
                });
        Util.showSnackbar(snackbar);
    }

    public void deleteAlbumItems(final AlbumItem[] selected_items, final int[] indices) {
        File_POJO[] filesToDelete = new File_POJO[selected_items.length];
        for (int i = 0; i < filesToDelete.length; i++) {
            filesToDelete[i] = new File_POJO(selected_items[i].getPath(), true);
        }

        registerLocalBroadcastReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case FileOperation.RESULT_DONE:
                        unregisterLocalBroadcastReceiver(this);
                        break;
                    case FileOperation.FAILED:
                        String path = intent.getStringExtra(FileOperation.FILES);
                        for (int i = 0; i < selected_items.length; i++) {
                            if (selected_items[i].getPath().equals(path)) {
                                int index = indices[i];
                                if (index < album.getAlbumItems().size()) {
                                    album.getAlbumItems().add(index, selected_items[i]);
                                } else {
                                    album.getAlbumItems().add(selected_items[i]);
                                }
                                recyclerViewAdapter.notifyItemInserted(indices[i]);
                                break;
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        });

        startService(FileOperation.getDefaultIntent(
                this, FileOperation.DELETE, filesToDelete));
    }

    //needed to send multiple uris in intents
    private ClipData createClipData(AlbumItem[] items) {
        String[] mimeTypes = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            mimeTypes[i] = MediaType.getMimeType(this, items[i].getUri(this));
        }

        ClipData clipData =
                new ClipData("Images", mimeTypes,
                        new ClipData.Item(items[0].getUri(this)));
        for (int i = 1; i < items.length; i++) {
            clipData.addItem(new ClipData.Item(items[i].getUri(this)));
        }
        return clipData;
    }

    public void setPhotosResult() {
        final AlbumItem[] selected_items = SelectorModeManager
                .createAlbumItemArray(recyclerViewAdapter.exitSelectorMode(true));

        Intent intent = new Intent("us.koller.RESULT_ACTION");
        if (allowMultiple) {
            ClipData clipData = createClipData(selected_items);
            intent.setClipData(clipData);
        } else {
            intent.setData(selected_items[0].getUri(this));
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        supportFinishAfterTransition();
    }

    @Override
    public void onSelectorModeEnter() {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setActivated(true);
        toolbar.animate().translationY(0.0f).start();

        if (theme.darkStatusBarIconsInSelectorMode()) {
            Util.setDarkStatusBarIcons(findViewById(R.id.root_view));
        } else {
            Util.setLightStatusBarIcons(findViewById(R.id.root_view));
        }

        if (!pick_photos) {
            ColorFade.fadeBackgroundColor(toolbar, toolbarColor, accentColor);
            //ColorFade.fadeToolbarTitleColor(toolbar, accentTextColor, null);
            selectedItemCount = 0;

            //fade overflow menu icon
            ColorFade.fadeDrawableColor(toolbar.getOverflowIcon(), textColorSecondary, accentTextColor);

            if (menu != null) {
                Drawable selectAll = menu.findItem(R.id.select_all).getIcon();
                selectAll.setAlpha(0);
                ColorFade.fadeDrawableAlpha(selectAll, 255);
            }

            ColorDrawable statusBarOverlay = getStatusBarOverlay();
            if (statusBarOverlay != null) {
                ColorFade.fadeDrawableAlpha(statusBarOverlay, 0);
            }

            Drawable navIcon = toolbar.getNavigationIcon();
            if (navIcon instanceof Animatable) {
                ((Animatable) navIcon).start();
                ColorFade.fadeDrawableColor(navIcon, textColorSecondary, accentTextColor);
            }
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Drawable d;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        AnimatedVectorDrawable drawable = (AnimatedVectorDrawable)
                                ContextCompat.getDrawable(AlbumActivity.this,
                                        R.drawable.cancel_to_back_avd);
                        //mutating avd to reset it
                        drawable.mutate();
                        d = drawable;
                    } else {
                        d = ContextCompat.getDrawable(AlbumActivity.this,
                                R.drawable.ic_clear_white);
                    }
                    d = DrawableCompat.wrap(d);
                    DrawableCompat.setTint(d.mutate(), accentTextColor);
                    toolbar.setNavigationIcon(d);
                }
            }, navIcon instanceof Animatable ? (int) (500 * Util.getAnimatorSpeed(this)) : 0);
        } else {
            toolbar.setBackgroundColor(accentColor);
            toolbar.setTitleTextColor(accentTextColor);
        }

        handleMenuVisibilityForSelectorMode(true);

        if (!pick_photos) {
            animateFab(true);
        }
    }

    @Override
    public void onSelectorModeExit() {
        if (pick_photos) {
            return;
        }

        final Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setActivated(theme.elevatedToolbar());

        if (theme.darkStatusBarIcons()) {
            Util.setDarkStatusBarIcons(findViewById(R.id.root_view));
        } else {
            Util.setLightStatusBarIcons(findViewById(R.id.root_view));
        }

        ColorDrawable statusBarOverlay = getStatusBarOverlay();
        if (statusBarOverlay != null) {
            int alpha = Color.alpha(getStatusBarColor());
            ColorFade.fadeDrawableAlpha(statusBarOverlay, alpha);
        }

        ColorFade.fadeBackgroundColor(toolbar, accentColor, toolbarColor);

        ColorFade.fadeToolbarTitleColor(toolbar, textColorPrimary,
                new ColorFade.ToolbarTitleFadeCallback() {
                    @Override
                    public void onSetTitle(Toolbar toolbar) {
                        toolbar.setTitle(album.getName());
                    }
                });

        final Drawable selectAll = menu.findItem(R.id.select_all).getIcon();
        ColorFade.fadeDrawableAlpha(selectAll, 0);

        //fade overflow menu icon
        ColorFade.fadeDrawableColor(toolbar.getOverflowIcon(), accentTextColor, textColorSecondary);

        Drawable navIcon = toolbar.getNavigationIcon();
        if (navIcon instanceof Animatable) {
            ((Animatable) navIcon).start();
            ColorFade.fadeDrawableColor(navIcon, accentTextColor, textColorSecondary);
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Drawable d;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AnimatedVectorDrawable drawable = (AnimatedVectorDrawable)
                            ContextCompat.getDrawable(AlbumActivity.this,
                                    R.drawable.back_to_cancel_avd);
                    //mutating avd to reset it
                    drawable.mutate();
                    d = drawable;
                } else {
                    d = ContextCompat.getDrawable(AlbumActivity.this,
                            R.drawable.ic_arrow_back_white);
                }
                d = DrawableCompat.wrap(d);
                DrawableCompat.setTint(d.mutate(), textColorSecondary);
                toolbar.setNavigationIcon(d);
                handleMenuVisibilityForSelectorMode(false);
                selectAll.setAlpha(100);
            }
        }, navIcon instanceof Animatable ? (int) (500 * Util.getAnimatorSpeed(this)) : 0);

        if (!pick_photos) {
            animateFab(false);
        }
    }

    @Override
    public void onItemSelected(int selectedItemCount) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        final String newTitle = getString(R.string.selected_count, selectedItemCount);
        if (selectedItemCount > 0 && this.selectedItemCount > 0)
            toolbar.setTitle(newTitle);
        else ColorFade.fadeToolbarTitleColor(toolbar, accentTextColor,
                new ColorFade.ToolbarTitleFadeCallback() {
                    @Override
                    public void onSetTitle(Toolbar toolbar) {
                        toolbar.setTitle(newTitle);
                    }
                });
        this.selectedItemCount = selectedItemCount;

        if (selectedItemCount > 0) {
            if (pick_photos) {
                if (allowMultiple) {
                    animateFab(true);
                } else {
                    setPhotosResult();
                }
            }
        } else {
            if (pick_photos) {
                animateFab(false);
            }
        }
    }

    //todo: keep the UI items selected until user click delete
    public void deleteSelectedItems() {
        //deleteAlbumItemsSnackbar();
        final String[] selected_items = recyclerViewAdapter
                .exitSelectorMode(true);
        new AlertDialog.Builder(AlbumActivity.this)
                .setTitle(getString(R.string.delete_files, selected_items.length) + "?")
                .setNegativeButton(getString(R.string.no), null)
                .setPositiveButton(getString(R.string.delete), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        deleteAlbumItemsSnackbar(selected_items);
                    }
                }).show();
    }

    public void fabClicked() {
        if (!pick_photos) {
            //deleteClicked();
            shareSelectedItems();
        } else {
            setPhotosResult();
        }
    }

    public void shareSelectedItems() {
        if (recyclerViewAdapter.getSelectorManager().getSelectedItemCount() <= 0) {
            recyclerViewAdapter.exitSelectorMode(false);
            return;
        }
        //share multiple items
        String[] selected_items_paths = recyclerViewAdapter.exitSelectorMode(true);
        ArrayList<Uri> uris = new ArrayList<>();
        for (int i = 0; i < selected_items_paths.length; i++) {
            uris.add(StorageUtil.getContentUri(this, selected_items_paths[i]));
        }

        Intent intent = new Intent()
                .setAction(Intent.ACTION_SEND_MULTIPLE)
                .setType(MediaType.getMimeType(this, uris.get(0)))
                .putExtra(Intent.EXTRA_STREAM, uris);

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(intent, getString(R.string.share)));
        }
    }

    public void animateFab(final boolean show) {
        //Log.i(TAG, "animateFab " + show + ", " + click);
        final FloatingActionButton fab = findViewById(R.id.fab);

        if ((fab.getScaleX() == 1.0f && show)
                || (fab.getScaleX() == 0.0f && !show)) {
            return;
        }

        if (show) {
            fab.setOnClickListener(view -> fabClicked());
        } else {
            fab.setOnClickListener(null);
        }
        AnimatorListenerAdapter l = show ? new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Drawable drawable = fab.getDrawable();
                if (drawable instanceof Animatable) {
                    ((Animatable) drawable).start();
                }
            }
        } : null;
        fab.animate()
                .scaleX(show ? 1.0f : 0.0f)
                .scaleY(show ? 1.0f : 0.0f)
                .alpha(show ? 1.0f : 0.0f)
                .setDuration(250)
                .setListener(l)
                .start();
    }

    @Override
    public void onBackPressed() {
        if (recyclerView != null && recyclerViewAdapter.onBackPressed()) {
            animateFab(false);
        } else {
            if (snackbar != null) {
                snackbar.dismiss();
                snackbar = null;
            }
            super.onBackPressed();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        //outState.putParcelable(ALBUM, album);
        if (recyclerView != null) {
            outState.putParcelable(RECYCLER_VIEW_SCROLL_STATE,
                    recyclerView.getLayoutManager().onSaveInstanceState());
            recyclerViewAdapter.saveInstanceState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Provider.saveExcludedPaths(this);
        Provider.savePinnedPaths(this);
    }

    @Override
    public boolean canSwipeBack(int dir) {
        return !recyclerViewAdapter.isSelectorModeActive() &&
                SwipeBackCoordinatorLayout.canSwipeBackForThisView(recyclerView, dir) && !pick_photos;
    }

    @Override
    public void onSwipeProcess(float fraction) {
        getWindow().getDecorView().setBackgroundColor(
                SwipeBackCoordinatorLayout.getBackgroundColor(fraction));
        boolean selectorModeActive = recyclerViewAdapter.isSelectorModeActive();
        if (!theme.darkStatusBarIcons() && selectorModeActive) {
            SwipeBackCoordinatorLayout layout = findViewById(R.id.swipeBackView);
            Toolbar toolbar = findViewById(R.id.toolbar);
            View rootView = findViewById(R.id.root_view);
            int translationY = (int) layout.getTranslationY();
            int statusBarHeight = toolbar.getPaddingTop();
            if (translationY > statusBarHeight * 0.5) {
                Util.setLightStatusBarIcons(rootView);
            } else {
                Util.setDarkStatusBarIcons(rootView);
            }
        }
    }

    @Override
    public void onSwipeFinish(int dir) {
        if (recyclerViewAdapter.isSelectorModeActive()) {
            recyclerViewAdapter.exitSelectorMode(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setReturnTransition(new TransitionSet()
                    .setOrdering(TransitionSet.ORDERING_TOGETHER)
                    .addTransition(new Slide(dir > 0 ? Gravity.TOP : Gravity.BOTTOM))
                    .addTransition(new Fade()));
            finishAfterTransition();
        } else finish();
    }

    @Override
    public int getDarkThemeRes() {
        return R.style.CameraRoll_Theme_Translucent_Album;
    }

    @Override
    public int getLightThemeRes() {
        return R.style.CameraRoll_Theme_Light_Translucent_Album;
    }

    @Override
    public void onThemeApplied(Theme theme) {
        if (pick_photos) {
            return;
        }

        final Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(toolbarColor);
        toolbar.setTitleTextColor(textColorPrimary);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setBackgroundTintList(ColorStateList.valueOf(accentColor));

        if (theme.darkStatusBarIcons()) {
            Util.setDarkStatusBarIcons(findViewById(R.id.root_view));
        } else {
            Util.setLightStatusBarIcons(findViewById(R.id.root_view));
        }

        if (theme.statusBarOverlay()) {
            addStatusBarOverlay(toolbar);
        }
    }

    @Override
    public IntentFilter getBroadcastIntentFilter() {
        IntentFilter filter = FileOperation.Util.getIntentFilter(super.getBroadcastIntentFilter());
        filter.addAction(ALBUM_ITEM_REMOVED);
        filter.addAction(ALBUM_ITEM_RENAMED);
        filter.addAction(DATA_CHANGED);
        return filter;
    }

    @Override
    public BroadcastReceiver getDefaultLocalBroadcastReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;
                //Log.i(TAG, "BroadcastReceiver " + action);
                switch (action) {
                    case FileOperation.RESULT_DONE:
                        int type = intent.getIntExtra(FileOperation.TYPE, FileOperation.EMPTY);
                        //String actionString = FileOperation.Util.getActionString(context, type);
                        //Log.i(TAG, "RESULT_DONE: FileOperation " + actionString);
                        switch (type) {
                            case FileOperation.MOVE: {
                                ArrayList<String> movedFilesPaths = intent
                                        .getStringArrayListExtra(Move.MOVED_FILES_PATHS);
                                for (int i = 0; i < movedFilesPaths.size(); i++) {
                                    String path = movedFilesPaths.get(i);
                                    removeAlbumItem(path);
                                }
                                break;
                            }
                            case FileOperation.COPY: {
                                //todo animate
                                String albumPath = getIntent().getStringExtra(ALBUM_PATH);
                                MediaProvider.getAlbum(AlbumActivity.this, albumPath,
                                        new MediaProvider.OnAlbumLoadedCallback() {
                                            @Override
                                            public void onAlbumLoaded(Album newAlbum) {
                                                ArrayList<AlbumItem> oldItems = album.getAlbumItems();
                                                ArrayList<AlbumItem> newItems = newAlbum.getAlbumItems();
                                                SortUtil.sort(newItems, Settings.getInstance(context).sortAlbumBy());
                                                String curOldItemPath = oldItems.get(0).getPath();
                                                for (int i = 0; i < newItems.size(); ++i) {
                                                    AlbumItem curNewItem = newItems.get(i);
                                                    if (curOldItemPath != null && curOldItemPath.equals(curNewItem.getPath())) {
                                                        if (i + 1 < oldItems.size())
                                                            curOldItemPath = oldItems.get(i + 1).getPath();
                                                        else {
                                                            curOldItemPath = null;
                                                        }
                                                    } else {
                                                        oldItems.add(i, curNewItem);
                                                        recyclerViewAdapter.notifyItemInserted(i);
                                                    }
                                                }
                                            }
                                        }, true);
                                break;
                            }
                            case FileOperation.RENAME: {
                                if (intent.getBooleanExtra(FileOperation.FAILED, false)) {
                                    Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show();
                                    break;
                                }
                                final Activity activity = AlbumActivity.this;

                                final String newFilePath = intent.getStringExtra(Rename.NEW_FILE_PATH);
                                getIntent().putExtra(ALBUM_PATH, newFilePath);

                                boolean hiddenFolders = Settings.getInstance(activity).getHiddenFolders();
                                new MediaProvider(activity).loadAlbums(activity, hiddenFolders,
                                        new MediaProvider.OnMediaLoadedCallback() {
                                            @Override
                                            public void onMediaLoaded(ArrayList<Album> albums) {
                                                //reload activity
                                                MediaProvider.getAlbum(activity, newFilePath,
                                                        new MediaProvider.OnAlbumLoadedCallback() {
                                                            @Override
                                                            public void onAlbumLoaded(@Nullable Album album) {
                                                                AlbumActivity.this.album = album;
                                                                AlbumActivity.this.onAlbumLoaded(null);
                                                            }
                                                        });
                                            }

                                            @Override
                                            public void timeout() {
                                                supportFinishAfterTransition();
                                            }

                                            @Override
                                            public void needPermission() {
                                                supportFinishAfterTransition();
                                            }
                                        });
                            }
                        }
                        break;
                    case ALBUM_ITEM_REMOVED: {
                        String path = intent.getStringExtra(ItemActivity.ALBUM_ITEM_PATH);
                        removeAlbumItem(path);
                        break;
                    }
                    case ALBUM_ITEM_RENAMED:
                    case DATA_CHANGED: {
                        String albumPath = getIntent().getStringExtra(ALBUM_PATH);
                        MediaProvider.getAlbum(AlbumActivity.this, albumPath,
                                new MediaProvider.OnAlbumLoadedCallback() {
                                    @Override
                                    public void onAlbumLoaded(Album album) {
                                        AlbumActivity.this.album = album;
                                        AlbumActivity.this.onAlbumLoaded(null);
                                    }
                                });
                        break;
                    }
                    default:
                        break;
                }
            }
        };
    }

    private void removeAlbumItem(String path) {
        //Log.d(TAG, "removeAlbumItem(" + path + ")");
        ArrayList<AlbumItem> albumItems = album.getAlbumItems();
        int size = albumItems.size();
        for (int i = 0; i < size; ++i) {
            String curPath = albumItems.get(i).getPath();
            if (curPath.equals(path)) {
                albumItems.remove(i);
                if (size == 1)
                    supportFinishAfterTransition();
                else
                    recyclerViewAdapter.notifyItemRemoved(i);
                return;
            }
        }

        //todo: property 'album' is shared with ItemActivity. This can cause problems.
        //Log.w(TAG, "todo: property 'album' is shared with ItemActivity. This can cause problems.");
        if (size == 0)
            supportFinishAfterTransition();
        else
            recyclerViewAdapter.notifyDataSetChanged();
    }
}
