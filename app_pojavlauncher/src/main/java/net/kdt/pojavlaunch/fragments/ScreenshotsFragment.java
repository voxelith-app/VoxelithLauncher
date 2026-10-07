package net.kdt.pojavlaunch.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import git.artdeell.mojo.R;

/** Screenshots of the selected instance, with small thumbnails so big galleries stay light. */
public class ScreenshotsFragment extends Fragment {
    public static final String TAG = "ScreenshotsFragment";
    private static final int THUMB_SIZE = 256;

    private final List<File> mFiles = new ArrayList<>();
    private final ThumbAdapter mAdapter = new ThumbAdapter();
    // RGB_565 thumbnails of 256 px are about 128 KB each, so this keeps a few dozen around
    private final LruCache<String, Bitmap> mThumbs = new LruCache<String, Bitmap>(
            (int) Math.min(8 * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private TextView mStatus;
    private Instance mInstance;

    public ScreenshotsFragment() {
        super(R.layout.fragment_screenshots);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mInstance = Instances.loadSelectedInstance();
        if(mInstance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            Tools.removeCurrentFragment(requireActivity());
            return;
        }
        ((TextView) view.findViewById(R.id.screenshots_instance)).setText(mInstance.name);
        mStatus = view.findViewById(R.id.screenshots_status);
        RecyclerView grid = view.findViewById(R.id.screenshots_grid);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        grid.setAdapter(mAdapter);
        reload();
    }

    private void reload() {
        final File folder = new File(mInstance.getGameDirectory(), "screenshots");
        PojavApplication.sExecutorService.execute(() -> {
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".jpg"));
            List<File> sorted = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
            java.util.Collections.sort(sorted, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            Tools.runOnUiThread(() -> {
                if(!isAdded() || getView() == null) return;
                mFiles.clear();
                mFiles.addAll(sorted);
                mAdapter.notifyDataSetChanged();
                mStatus.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Nullable
    private static Bitmap decode(File file, int targetSize, boolean lowQuality) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if(bounds.outWidth <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while(bounds.outWidth / (options.inSampleSize * 2) >= targetSize) options.inSampleSize *= 2;
        if(lowQuality) options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private void open(File file) {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_screenshot, null);
        ImageView image = content.findViewById(R.id.screenshot_full);
        ((TextView) content.findViewById(R.id.screenshot_info)).setText(file.getName() + " · "
                + Formatter.formatShortFileSize(requireContext(), file.length()) + " · "
                + DateUtils.getRelativeTimeSpanString(file.lastModified(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        int width = getResources().getDisplayMetrics().widthPixels;
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = decode(file, width, false);
            Tools.runOnUiThread(() -> image.setImageBitmap(bitmap));
        });
        new AlertDialog.Builder(requireContext())
                .setView(content)
                .setPositiveButton(R.string.screenshots_share, (d, w) -> Tools.openPath(requireContext(), file, true))
                .setNeutralButton(R.string.worlds_delete, (d, w) -> confirmDelete(file))
                .setNegativeButton(R.string.crash_close, null)
                .show();
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.screenshots_delete_confirm)
                .setPositiveButton(R.string.worlds_delete, (d, w) -> {
                    if(file.delete()) {
                        mThumbs.remove(file.getAbsolutePath());
                        reload();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private class ThumbAdapter extends RecyclerView.Adapter<ThumbAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            String path;

            Holder(View view) {
                super(view);
                image = (ImageView) view;
                image.setClipToOutline(true);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_screenshot, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            File file = mFiles.get(position);
            String path = file.getAbsolutePath();
            holder.path = path;
            holder.itemView.setOnClickListener(v -> open(file));
            Bitmap cached = mThumbs.get(path);
            holder.image.setImageBitmap(cached);
            if(cached != null) return;
            PojavApplication.sExecutorService.execute(() -> {
                Bitmap thumb = decode(file, THUMB_SIZE, true);
                if(thumb == null) return;
                Tools.runOnUiThread(() -> {
                    mThumbs.put(path, thumb);
                    if(path.equals(holder.path)) holder.image.setImageBitmap(thumb);
                });
            });
        }

        @Override
        public int getItemCount() {
            return mFiles.size();
        }
    }
}
