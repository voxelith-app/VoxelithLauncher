package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.mods.ContentFragments;
import net.kdt.pojavlaunch.mods.ContentInstaller;
import net.kdt.pojavlaunch.mods.ContentTarget;
import net.kdt.pojavlaunch.mods.ContentType;
import net.kdt.pojavlaunch.mods.ContentViewHolder;
import net.kdt.pojavlaunch.mods.InstalledContent;
import net.kdt.pojavlaunch.mods.ModrinthClient;
import net.kdt.pojavlaunch.mods.ModrinthModels;
import net.kdt.pojavlaunch.mods.PerformancePack;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import git.artdeell.mojo.R;

/** Lists the mods, resource packs and shaders of the selected instance. */
public class InstanceContentFragment extends Fragment {
    public static final String TAG = "InstanceContentFragment";

    private final ModrinthClient mClient = new ModrinthClient();
    private final List<InstalledContent> mItems = new ArrayList<>();
    private ModIconCache mIconCache;
    private ContentAdapter mAdapter;
    private ContentType mType;
    private ContentTarget mTarget;
    private ProgressBar mProgress;
    private TextView mStatus;
    private View mOptimizeButton;
    private View mUpdateAllButton;
    private int mLoadGeneration;

    public InstanceContentFragment() {
        super(R.layout.fragment_instance_content);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            Tools.removeCurrentFragment(requireActivity());
            return;
        }
        mType = ContentFragments.typeFrom(getArguments());
        mTarget = ContentTarget.fromInstance(instance);
        mIconCache = ContentViewHolder.sharedIconCache();
        mProgress = view.findViewById(R.id.content_progress);
        mStatus = view.findViewById(R.id.content_status);

        ((TextView) view.findViewById(R.id.content_instance_name)).setText(instance.name);
        ((TextView) view.findViewById(R.id.content_instance_target)).setText(ContentFragments.describeTarget(requireContext(), mTarget));

        RecyclerView list = view.findViewById(R.id.content_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new ContentAdapter();
        list.setAdapter(mAdapter);

        mUpdateAllButton = view.findViewById(R.id.content_update_all);
        mUpdateAllButton.setOnClickListener(v -> updateAll());
        mOptimizeButton = view.findViewById(R.id.content_optimize);
        mOptimizeButton.setOnClickListener(v -> confirmOptimize());
        view.findViewById(R.id.content_add).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ContentBrowserFragment.class, ContentBrowserFragment.TAG, ContentFragments.args(mType)));
        ContentFragments.setupTabs(view.findViewById(R.id.content_tabs), mType, type -> {
            mType = type;
            setArguments(ContentFragments.args(type));
            reload();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mTarget != null) reload();
    }

    private void reload() {
        final int generation = ++mLoadGeneration;
        final ContentType type = mType;
        mOptimizeButton.setVisibility(type == ContentType.MOD && mTarget.supportsMods() ? View.VISIBLE : View.GONE);
        mUpdateAllButton.setVisibility(View.GONE);
        mItems.clear();
        mAdapter.notifyDataSetChanged();
        mStatus.setVisibility(View.GONE);
        if(type == ContentType.MOD && !mTarget.supportsMods()) {
            showStatus(R.string.content_no_loader);
            return;
        }
        mProgress.setVisibility(View.VISIBLE);
        PojavApplication.sExecutorService.execute(() -> {
            List<InstalledContent> contents;
            try {
                contents = InstalledContent.scan(mTarget.getFolder(type), type);
            }catch (Exception e) {
                Tools.runOnUiThread(() -> { if(isCurrent(generation)) showError(e); });
                return;
            }
            Tools.runOnUiThread(() -> { if(isCurrent(generation)) showItems(contents, true); });
            if(contents.isEmpty()) return;
            boolean identified = true;
            try {
                InstalledContent.identify(mClient, contents, type, mTarget);
            }catch (Exception e) {
                identified = false;
            }
            final boolean ok = identified;
            Tools.runOnUiThread(() -> {
                if(!isCurrent(generation)) return;
                showItems(contents, false);
                if(!ok) Toast.makeText(requireContext(), R.string.content_identify_failed, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private boolean isCurrent(int generation) {
        return generation == mLoadGeneration && isAdded() && getView() != null;
    }

    private void showItems(List<InstalledContent> contents, boolean stillLoading) {
        mProgress.setVisibility(stillLoading && !contents.isEmpty() ? View.VISIBLE : View.GONE);
        mItems.clear();
        mItems.addAll(contents);
        mAdapter.notifyDataSetChanged();
        boolean hasUpdates = false;
        for(InstalledContent content : contents) if(content.update != null) hasUpdates = true;
        mUpdateAllButton.setVisibility(hasUpdates && !stillLoading ? View.VISIBLE : View.GONE);
        if(contents.isEmpty()) showStatus(R.string.content_empty);
        else mStatus.setVisibility(View.GONE);
    }

    private void showStatus(int text) {
        mProgress.setVisibility(View.GONE);
        mStatus.setText(text);
        mStatus.setVisibility(View.VISIBLE);
    }

    private void showError(Exception e) {
        mProgress.setVisibility(View.GONE);
        Tools.showError(requireContext(), e);
    }

    private void confirmDelete(InstalledContent content) {
        new AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.content_remove_confirm, content.displayName()))
                .setPositiveButton(R.string.content_remove, (d, w) -> {
                    if(content.file.delete()) {
                        int index = mItems.indexOf(content);
                        if(index >= 0) {
                            mItems.remove(index);
                            mAdapter.notifyItemRemoved(index);
                        }
                        if(mItems.isEmpty()) showStatus(R.string.content_empty);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void update(InstalledContent content) {
        if(ProgressKeeper.getTaskCount() != 0) {
            Toast.makeText(requireContext(), R.string.content_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        final ContentType type = mType;
        final String name = content.displayName();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                new ContentInstaller(mClient, mTarget).update(content, type);
                Tools.runOnUiThread(() -> {
                    if(!isAdded()) return;
                    Toast.makeText(requireContext(), getString(R.string.content_updated_toast, name), Toast.LENGTH_SHORT).show();
                    reload();
                });
            }catch (Exception e) {
                Tools.showErrorRemote(e);
            }
        });
    }

    private void updateAll() {
        if(ProgressKeeper.getTaskCount() != 0) {
            Toast.makeText(requireContext(), R.string.content_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        final ContentType type = mType;
        final List<InstalledContent> contents = new ArrayList<>(mItems);
        mUpdateAllButton.setVisibility(View.GONE);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                int count = new ContentInstaller(mClient, mTarget).updateAll(contents, type);
                Tools.runOnUiThread(() -> {
                    if(!isAdded()) return;
                    Toast.makeText(requireContext(), getString(R.string.content_updated_all, count), Toast.LENGTH_SHORT).show();
                    reload();
                });
            }catch (Exception e) {
                Tools.showErrorRemote(e);
            }
        });
    }

    private void confirmOptimize() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.content_optimize_title)
                .setMessage(R.string.content_optimize_message)
                .setPositiveButton(R.string.content_optimize, (d, w) -> optimize())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void optimize() {
        if(ProgressKeeper.getTaskCount() != 0 || mTarget.loader == null) {
            Toast.makeText(requireContext(), R.string.content_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        final ContentTarget target = mTarget;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                List<InstalledContent> installed = InstalledContent.scan(target.getFolder(ContentType.MOD), ContentType.MOD);
                Set<String> installedIds = new HashSet<>();
                try {
                    List<String> hashes = new ArrayList<>(installed.size());
                    for(InstalledContent content : installed) hashes.add(content.sha1);
                    for(ModrinthModels.Version version : mClient.identify(hashes).values()) {
                        if(version != null) installedIds.add(version.projectId);
                    }
                }catch (IOException ignored) {}
                List<ModrinthModels.Version> versions = new ContentInstaller(mClient, target)
                        .installGroups(PerformancePack.groupsFor(target.loader), ContentType.MOD, installedIds);
                PerformancePack.applyLightOptions(target.gameDirectory);
                final int count = versions.size();
                Tools.runOnUiThread(() -> {
                    if(!isAdded()) return;
                    String message = count > 0
                            ? getString(R.string.content_optimize_done, count)
                            : getString(R.string.content_optimize_nothing);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    reload();
                });
            }catch (Exception e) {
                Tools.showErrorRemote(e);
            }
        });
    }

    private class ContentAdapter extends RecyclerView.Adapter<ContentViewHolder> {
        @NonNull
        @Override
        public ContentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_content, parent, false);
            return new ContentViewHolder(view, mIconCache);
        }

        @Override
        public void onBindViewHolder(@NonNull ContentViewHolder holder, int position) {
            InstalledContent content = mItems.get(position);
            holder.title.setText(content.displayName());
            holder.subtitle.setText(content.versionName != null ? content.versionName : content.file.getName());
            holder.loadIcon(content.projectId, content.iconUrl);
            holder.title.setAlpha(content.isEnabled() ? 1f : 0.5f);

            holder.enabledSwitch.setVisibility(View.VISIBLE);
            holder.enabledSwitch.setOnCheckedChangeListener(null);
            holder.enabledSwitch.setChecked(content.isEnabled());
            holder.enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
                if(!content.setEnabled(checked)) button.setChecked(!checked);
                holder.title.setAlpha(content.isEnabled() ? 1f : 0.5f);
            });

            holder.deleteButton.setVisibility(View.VISIBLE);
            holder.deleteButton.setOnClickListener(v -> confirmDelete(content));

            holder.updateButton.setVisibility(content.update != null ? View.VISIBLE : View.GONE);
            holder.updateButton.setOnClickListener(v -> update(content));
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }
}
