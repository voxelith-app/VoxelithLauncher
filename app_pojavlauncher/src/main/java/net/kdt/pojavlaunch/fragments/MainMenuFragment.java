package net.kdt.pojavlaunch.fragments;

import android.net.Uri;

import androidx.activity.result.contract.ActivityResultContracts;

import net.kdt.pojavlaunch.instances.InstanceBackup;

import java.io.OutputStream;
import android.widget.ImageView;

import net.kdt.pojavlaunch.servers.QuickPlay;
import net.kdt.pojavlaunch.stats.PlayTime;
import net.kdt.pojavlaunch.worlds.WorldStore;

import java.util.Collections;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.servers.SavedServer;
import net.kdt.pojavlaunch.servers.ServerStore;

import java.util.ArrayList;
import java.util.List;
import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    @SuppressWarnings("deprecation") // The mime type constructor needs a newer androidx.activity
    private final ActivityResultLauncher<String> mBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument(), this::exportInstance);
    private boolean mExporting;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        Button mModsButton = view.findViewById(R.id.mods_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> openGameDirectory(v.getContext()));

        mModsButton.setOnClickListener((v)-> openWorlds());
        view.findViewById(R.id.modpacks_button).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ModpacksFragment.class, ModpacksFragment.TAG, null));
        view.findViewById(R.id.screenshots_button).setOnClickListener(v -> {
            if(Instances.loadSelectedInstance() == null) {
                Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
                return;
            }
            Tools.swapFragment(requireActivity(), ScreenshotsFragment.class, ScreenshotsFragment.TAG, null);
        });
        view.findViewById(R.id.backup_button).setOnClickListener(v -> {
            Instance instance = Instances.loadSelectedInstance();
            if(instance == null) {
                Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
                return;
            }
            if(!mExporting) mBackupLauncher.launch(instance.name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".zip");
        });


        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }

    private void exportInstance(@Nullable Uri uri) {
        Instance instance = Instances.loadSelectedInstance();
        if(uri == null || instance == null) return;
        mExporting = true;
        Context context = requireContext().getApplicationContext();
        Toast.makeText(context, getString(R.string.instance_backup_exporting, instance.name), Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            String message;
            try(OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if(out == null) throw new java.io.IOException("no output");
                InstanceBackup.export(instance, out);
                message = context.getString(R.string.instance_backup_exported, instance.name);
            }catch (Exception e) {
                message = context.getString(R.string.worlds_error, e.getMessage());
            }
            final String result = message;
            Tools.runOnUiThread(() -> {
                mExporting = false;
                Toast.makeText(context, result, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void openWorlds() {
        if(Instances.loadSelectedInstance() == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        Tools.swapFragment(requireActivity(), WorldsFragment.class, WorldsFragment.TAG, null);
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        loadRecentServers();
    }

    private static class RecentEntry {
        final String title;
        final String detail;
        final long time;
        final boolean world;
        @Nullable final SavedServer server;
        @Nullable final String worldFolder;

        RecentEntry(String title, String detail, long time, @Nullable SavedServer server, @Nullable String worldFolder) {
            this.title = title;
            this.detail = detail;
            this.time = time;
            this.server = server;
            this.worldFolder = worldFolder;
            this.world = worldFolder != null;
        }
    }

    private void loadRecentServers() {
        View view = getView();
        if(view == null) return;
        PojavApplication.sExecutorService.execute(() -> {
            Instance instance = Instances.loadSelectedInstance();
            List<RecentEntry> recent = new ArrayList<>();
            for(SavedServer server : ServerStore.loadAll()) {
                if(server.lastPlayed <= 0) break;
                recent.add(new RecentEntry(server.name, server.address, server.lastPlayed, server, null));
                if(recent.size() == 3) break;
            }
            PlayTime.Stats stats = null;
            if(instance != null) {
                for(WorldStore.World world : WorldStore.list(instance.getGameDirectory())) {
                    if(world.lastPlayed <= 0) continue;
                    recent.add(new RecentEntry(world.name, instance.name, world.lastPlayed, null, world.folder.getName()));
                    if(recent.size() >= 6) break;
                }
                stats = PlayTime.get(instance);
            }
            Collections.sort(recent, (a, b) -> Long.compare(b.time, a.time));
            while(recent.size() > 3) recent.remove(recent.size() - 1);
            final PlayTime.Stats instanceStats = stats;
            Tools.runOnUiThread(() -> {
                showRecent(recent);
                showPlayTime(instanceStats);
            });
        });
    }

    private void showPlayTime(@Nullable PlayTime.Stats stats) {
        View view = getView();
        if(!isAdded() || view == null) return;
        TextView text = view.findViewById(R.id.home_playtime);
        if(text == null) return;
        if(stats == null || stats.totalMillis <= 0) {
            text.setVisibility(View.GONE);
            return;
        }
        CharSequence last = DateUtils.getRelativeTimeSpanString(stats.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        text.setText(getString(R.string.playtime_summary, PlayTime.format(requireContext(), stats.totalMillis), last));
        text.setVisibility(View.VISIBLE);
    }

    private void showRecent(List<RecentEntry> recent) {
        View view = getView();
        if(!isAdded() || view == null) return;
        View section = view.findViewById(R.id.recent_section);
        LinearLayout list = view.findViewById(R.id.recent_list);
        if(section == null || list == null) return;
        list.removeAllViews();
        section.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        long now = System.currentTimeMillis();
        for(RecentEntry entry : recent) {
            View row = inflater.inflate(R.layout.item_recent_server, list, false);
            ((ImageView) row.findViewById(R.id.recent_icon)).setImageResource(entry.world ? R.drawable.ic_world : R.drawable.ic_nav_servers);
            ((TextView) row.findViewById(R.id.recent_name)).setText(entry.title);
            CharSequence when = DateUtils.getRelativeTimeSpanString(entry.time, now, DateUtils.MINUTE_IN_MILLIS);
            ((TextView) row.findViewById(R.id.recent_detail)).setText(entry.detail + " · " + when);
            row.setOnClickListener(v -> {
                if(entry.server != null) {
                    ServersFragment.join(requireContext(), entry.server);
                }else if(entry.worldFolder != null) {
                    QuickPlay.requestWorld(entry.worldFolder);
                    ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
                }
            });
            list.addView(row);
        }
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
