package us.koller.cameraroll.adapter;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import us.koller.cameraroll.data.models.AlbumItem;

//simple wrapper class to handle the Selector Mode and selected items
public class SelectorModeManager {

    private static final String SELECTOR_MODE_ACTIVE = "SELECTOR_MODE_ACTIVE";
    private static final String SELECTED_ITEMS_PATHS = "SELECTED_ITEMS_PATHS";

    private boolean selectorModeActive = false;
    private List<String> selected_items_paths;

    private ArrayList<Callback> callbacks;

    //to handle backPressed in SelectorMode
    private OnBackPressedCallback onBackPressedCallback;

    //SelectorMode Callbacks
    public interface OnBackPressedCallback {
        void onBackPressed();
    }

    public interface Callback {
        void onSelectorModeEnter();

        void onSelectorModeExit();

        void onItemSelected(int selectedItemCount);
    }

    public static class SimpleCallback implements Callback {
        @Override
        public void onSelectorModeEnter() {

        }

        @Override
        public void onSelectorModeExit() {

        }

        @Override
        public void onItemSelected(int selectedItemCount) {

        }
    }

    public void onSelectorModeEnter() {
        if (callbacks != null) {
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).onSelectorModeEnter();
            }
        }
    }

    private void onSelectorModeExit() {
        if (callbacks != null) {
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).onSelectorModeExit();
            }
        }
    }

    public void onItemSelected(int selectedItemCount) {
        if (callbacks != null) {
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).onItemSelected(selectedItemCount);
            }
        }
    }

    public SelectorModeManager(Bundle savedState) {
        if (savedState.containsKey(SELECTOR_MODE_ACTIVE)) {
            setSelectorMode(Boolean.parseBoolean(savedState.getString(SELECTOR_MODE_ACTIVE)));
        }

        selected_items_paths = new LinkedList<>();
        if (isSelectorModeActive() && savedState.containsKey(SELECTED_ITEMS_PATHS)) {
            String[] stringArray = savedState.getStringArray(SELECTED_ITEMS_PATHS);
            if (stringArray != null) {
                selected_items_paths.addAll(Arrays.asList(stringArray));
            }
        }
    }

    public SelectorModeManager() {
        selected_items_paths = new LinkedList<>();
    }

    public boolean isItemSelected(String path) {
        return selected_items_paths.contains(path);
    }

    public void setSelectorMode(boolean selectorMode) {
        if (this.selectorModeActive != selectorMode) {
            this.selectorModeActive = selectorMode;
            if (selectorMode) {
                onSelectorModeEnter();
            } else {
                onSelectorModeExit();
            }
        }
    }

    public boolean isSelectorModeActive() {
        return selectorModeActive;
    }

    public boolean onToggleItemSelection(String path) {
        boolean selected = toggleSelectedPath(selected_items_paths, path);
        onItemSelected(getSelectedItemCount());
        return selected;
    }

    public void selectAll(String[] paths) {
        ArrayList<String> itemsToSelect = new ArrayList<>();
        for (String path : paths) {
            if (!selected_items_paths.contains(path)) {
                itemsToSelect.add(path);
            }
        }
        selected_items_paths.addAll(itemsToSelect);
        if (callbacks != null) {
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).onItemSelected(selected_items_paths.size());
            }
        }
    }

    public int getSelectedItemCount() {
        return selected_items_paths.size();
    }

    public String[] createStringArray() {
        return selected_items_paths.toArray(new String[0]);
    }

    public void clearList() {
        this.selected_items_paths = new LinkedList<>();
    }

    void saveInstanceState(Bundle outState) {
        boolean active = isSelectorModeActive();
        outState.putString(SELECTOR_MODE_ACTIVE, String.valueOf(active));
        if (active) {
            outState.putStringArray(SELECTED_ITEMS_PATHS, createStringArray());
        }
    }

    public void addCallback(Callback callback) {
        if (callbacks == null) {
            callbacks = new ArrayList<>();
        }
        callbacks.add(callback);

        if (isSelectorModeActive()) {
            callback.onSelectorModeEnter();
            callback.onItemSelected(getSelectedItemCount());
        }
    }

    public ArrayList<Callback> getCallbacks() {
        return callbacks;
    }

    public void setOnBackPressedCallback(OnBackPressedCallback onBackPressedCallback) {
        this.onBackPressedCallback = onBackPressedCallback;
    }

    public boolean onBackPressedCallbackAlreadySet() {
        return onBackPressedCallback != null;
    }

    public boolean onBackPressed() {
        if (onBackPressedCallback != null && isSelectorModeActive()) {
            onBackPressedCallback.onBackPressed();
            return true;
        } else {
            return false;
        }
    }


    //Util methods
    private static boolean toggleSelectedPath(List<String> arr, String item) {
        if (arr.contains(item)) {
            //remove item
            arr.remove(item);
            return false;
        } else {
            //add item
            arr.add(item);
            return true;
        }
    }

    public static AlbumItem[] createAlbumItemArray(String[] arr) {
        ArrayList<String> arrayList = new ArrayList<>(Arrays.asList(arr));
        return createAlbumItemArray(arrayList);
    }

    private static AlbumItem[] createAlbumItemArray(ArrayList<String> arr) {
        AlbumItem[] albumItems = new AlbumItem[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            albumItems[i] = AlbumItem.getInstance(arr.get(i));
        }
        return albumItems;
    }
}
