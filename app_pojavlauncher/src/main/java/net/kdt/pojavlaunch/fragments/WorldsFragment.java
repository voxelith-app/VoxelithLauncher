package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.servers.QuickPlay;
import net.kdt.pojavlaunch.stats.PlayTime;
import net.kdt.pojavlaunch.worlds.WorldStore;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import git.artdeell.mojo.R;

/** Singleplayer worlds of the selected instance, with play time, backups and import. */
public class WorldsFragment extends Fragment {
    public static final String TAG = "WorldsFragment";

    private final List<WorldStore.World> mWorlds = new ArrayList<>();
    private final Map<String, Bitmap> mIcons = new HashMap<>();
    private final WorldAdapter mAdapter = new WorldAdapter();
    private Instance mInstance;
    private TextView mStatus;
    private boolean mBusy;
    private int mGeneration;

    private final ActivityResultLauncher<String[]> mImportLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importWorld);

    public WorldsFragment() {
        super(R.layout.fragment_worlds);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mInstance = Instances.loadSelectedInstance();
        if(mInstance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            Tools.removeCurrentFragment(requireActivity());
            return;
        }
        mStatus = view.findViewById(R.id.worlds_status);
        RecyclerView list = view.findViewById(R.id.worlds_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(mAdapter);
        ((TextView) view.findViewById(R.id.worlds_instance)).setText(mInstance.name);
        view.findViewById(R.id.worlds_import).setOnClickListener(v -> {
            if(!mBusy) mImportLauncher.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        });
        view.findViewById(R.id.worlds_backups).setOnClickListener(v -> showBackups());
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mInstance != null) reload();
    }

    private void reload() {
        final int generation = ++mGeneration;
        final Instance instance = mInstance;
        PojavApplication.sExecutorService.execute(() -> {
            List<WorldStore.World> worlds = WorldStore.list(instance.getGameDirectory());
            Map<String, Bitmap> icons = new HashMap<>();
            for(WorldStore.World world : worlds) {
                File icon = world.icon();
                if(icon == null) continue;
                Bitmap bitmap = BitmapFactory.decodeFile(icon.getAbsolutePath());
                if(bitmap != null) icons.put(world.folder.getAbsolutePath(), bitmap);
            }
            PlayTime.Stats stats = PlayTime.get(instance);
            Tools.runOnUiThread(() -> {
                if(!isAdded() || getView() == null || generation != mGeneration) return;
                mWorlds.clear();
                mWorlds.addAll(worlds);
                mIcons.clear();
                mIcons.putAll(icons);
                mAdapter.notifyDataSetChanged();
                mStatus.setVisibility(worlds.isEmpty() ? View.VISIBLE : View.GONE);
                showStats(stats);
            });
        });
    }

    private void showStats(@Nullable PlayTime.Stats stats) {
        TextView text = requireView().findViewById(R.id.worlds_playtime);
        if(stats == null || stats.totalMillis <= 0) {
            text.setText(R.string.playtime_never);
            return;
        }
        CharSequence last = DateUtils.getRelativeTimeSpanString(stats.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        text.setText(getString(R.string.playtime_summary, PlayTime.format(requireContext(), stats.totalMillis), last));
    }

    private void play(WorldStore.World world) {
        QuickPlay.requestWorld(world.folder.getName());
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void showMenu(View anchor, WorldStore.World world) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.worlds_backup);
        menu.getMenu().add(0, 2, 1, R.string.worlds_open_folder);
        menu.getMenu().add(0, 3, 2, R.string.worlds_delete);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: backup(world); return true;
                case 2: Tools.openPath(requireContext(), world.folder, false); return true;
                case 3: confirmDelete(world); return true;
            }
            return false;
        });
        menu.show();
    }

    private void backup(WorldStore.World world) {
        if(mBusy) return;
        mBusy = true;
        Context context = requireContext().getApplicationContext();
        String instanceId = mInstance.getId();
        Toast.makeText(context, getString(R.string.worlds_backup_started, world.name), Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            String message;
            try {
                File backup = WorldStore.backup(world, instanceId);
                message = context.getString(R.string.worlds_backup_done, Formatter.formatShortFileSize(context, backup.length()));
            }catch (Exception e) {
                message = context.getString(R.string.worlds_error, e.getMessage());
            }
            final String result = message;
            Tools.runOnUiThread(() -> {
                mBusy = false;
                Toast.makeText(context, result, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void confirmDelete(WorldStore.World world) {
        new AlertDialog.Builder(requireContext())
                .setTitle(world.name)
                .setMessage(R.string.worlds_delete_confirm)
                .setPositiveButton(R.string.worlds_delete, (d, w) -> PojavApplication.sExecutorService.execute(() -> {
                    try {
                        WorldStore.delete(world);
                    }catch (Exception e) {
                        Tools.showErrorRemote(e);
                    }
                    Tools.runOnUiThread(() -> {
                        if(isAdded()) reload();
                    });
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showBackups() {
        final Context context = requireContext();
        final String instanceId = mInstance.getId();
        PojavApplication.sExecutorService.execute(() -> {
            List<File> backups = WorldStore.listBackups(instanceId);
            Tools.runOnUiThread(() -> {
                if(!isAdded()) return;
                if(backups.isEmpty()) {
                    Toast.makeText(context, R.string.worlds_no_backups, Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[backups.size()];
                for(int i = 0; i < names.length; i++) {
                    File file = backups.get(i);
                    names[i] = file.getName().replace(".zip", "") + "  ·  " + Formatter.formatShortFileSize(context, file.length());
                }
                new AlertDialog.Builder(context)
                        .setTitle(R.string.worlds_backups)
                        .setItems(names, (d, which) -> confirmRestore(backups.get(which)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private void confirmRestore(File backup) {
        new AlertDialog.Builder(requireContext())
                .setTitle(backup.getName())
                .setMessage(R.string.worlds_restore_confirm)
                .setPositiveButton(R.string.worlds_restore, (d, w) -> restore(backup))
                .setNeutralButton(R.string.worlds_delete_backup, (d, w) -> {
                    boolean ignored = backup.delete();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void restore(File backup) {
        if(mBusy) return;
        mBusy = true;
        final Context context = requireContext().getApplicationContext();
        final File gameDirectory = mInstance.getGameDirectory();
        PojavApplication.sExecutorService.execute(() -> {
            String message;
            try {
                File world = WorldStore.restore(backup, gameDirectory);
                message = context.getString(R.string.worlds_restored, world.getName());
            }catch (Exception e) {
                message = context.getString(R.string.worlds_error, e.getMessage());
            }
            finishTask(context, message);
        });
    }

    private void importWorld(@Nullable Uri uri) {
        if(uri == null || mBusy) return;
        mBusy = true;
        final Context context = requireContext().getApplicationContext();
        final File gameDirectory = mInstance.getGameDirectory();
        Toast.makeText(context, R.string.worlds_importing, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            String message;
            try(InputStream in = context.getContentResolver().openInputStream(uri)) {
                if(in == null) throw new java.io.IOException("empty file");
                String name = Tools.getFileName(context, uri);
                if(name == null) name = "Mundo";
                if(name.toLowerCase().endsWith(".zip")) name = name.substring(0, name.length() - 4);
                File world = WorldStore.importZip(in, name, gameDirectory);
                message = context.getString(R.string.worlds_restored, world.getName());
            }catch (Exception e) {
                message = context.getString(R.string.worlds_error, e.getMessage());
            }
            finishTask(context, message);
        });
    }

    private void finishTask(Context context, String message) {
        Tools.runOnUiThread(() -> {
            mBusy = false;
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            if(isAdded()) reload();
        });
    }

    private String gameMode(WorldStore.World world) {
        if(world.hardcore) return getString(R.string.worlds_mode_hardcore);
        switch (world.gameType) {
            case 1: return getString(R.string.worlds_mode_creative);
            case 2: return getString(R.string.worlds_mode_adventure);
            case 3: return getString(R.string.worlds_mode_spectator);
            default: return getString(R.string.worlds_mode_survival);
        }
    }

    private class WorldAdapter extends RecyclerView.Adapter<WorldAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView meta;
            final Button play;
            final View more;

            Holder(View view) {
                super(view);
                icon = view.findViewById(R.id.world_icon);
                name = view.findViewById(R.id.world_name);
                meta = view.findViewById(R.id.world_meta);
                play = view.findViewById(R.id.world_play);
                more = view.findViewById(R.id.world_more);
                icon.setClipToOutline(true);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_world, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            WorldStore.World world = mWorlds.get(position);
            holder.name.setText(world.name);
            Bitmap icon = mIcons.get(world.folder.getAbsolutePath());
            if(icon != null) {
                holder.icon.setPadding(0, 0, 0, 0);
                holder.icon.setImageBitmap(icon);
            }else {
                int padding = (int) (12 * holder.icon.getResources().getDisplayMetrics().density);
                holder.icon.setPadding(padding, padding, padding, padding);
                holder.icon.setImageResource(R.drawable.ic_world);
            }
            StringBuilder meta = new StringBuilder(gameMode(world));
            if(world.version != null) meta.append(" · ").append(world.version);
            if(world.lastPlayed > 0) {
                meta.append(" · ").append(DateUtils.getRelativeTimeSpanString(world.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            }
            holder.meta.setText(meta);
            holder.play.setOnClickListener(v -> play(world));
            holder.more.setOnClickListener(v -> showMenu(v, world));
            holder.itemView.setOnLongClickListener(v -> {
                showMenu(holder.more, world);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mWorlds.size();
        }
    }
}
