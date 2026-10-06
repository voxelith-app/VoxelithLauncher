package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import git.artdeell.mojo.R;

/** Searches Modrinth for content compatible with the selected instance and installs it. */
public class ContentBrowserFragment extends Fragment {
    public static final String TAG = "ContentBrowserFragment";

    private final ModrinthClient mClient = new ModrinthClient();
    private final List<ModrinthModels.SearchHit> mHits = new ArrayList<>();
    /** Accessed from the UI thread only. */
    private final Set<String> mInstalledProjects = new HashSet<>();
    private final Set<String> mInstallingProjects = new HashSet<>();
    private ModIconCache mIconCache;
    private BrowserAdapter mAdapter;
    private ContentType mType;
    private ContentTarget mTarget;
    private EditText mSearch;
    private ProgressBar mProgress;
    private TextView mStatus;
    private String mQuery = "";
    private int mSearchGeneration;
    private int mTotalHits;
    private boolean mLoadingPage;

    public ContentBrowserFragment() {
        super(R.layout.fragment_content_browser);
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
        mSearch = view.findViewById(R.id.browser_search);
        mProgress = view.findViewById(R.id.browser_progress);
        mStatus = view.findViewById(R.id.browser_status);
        ((TextView) view.findViewById(R.id.browser_target)).setText(
                instance.name + " · " + ContentFragments.describeTarget(requireContext(), mTarget));

        RecyclerView list = view.findViewById(R.id.browser_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        list.setLayoutManager(layoutManager);
        mAdapter = new BrowserAdapter();
        list.setAdapter(mAdapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if(dy <= 0 || mLoadingPage || mHits.size() >= mTotalHits) return;
                if(layoutManager.findLastVisibleItemPosition() >= mHits.size() - 4) loadPage(false);
            }
        });

        mSearch.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if(actionId != EditorInfo.IME_ACTION_SEARCH && !enter) return false;
            mQuery = mSearch.getText().toString().trim();
            hideKeyboard();
            loadPage(true);
            return true;
        });
        ContentFragments.setupTabs(view.findViewById(R.id.browser_tabs), mType, type -> {
            mType = type;
            setArguments(ContentFragments.args(type));
            refreshInstalled();
            loadPage(true);
        });

        refreshInstalled();
        loadPage(true);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if(imm != null) imm.hideSoftInputFromWindow(mSearch.getWindowToken(), 0);
        mSearch.clearFocus();
    }

    /** Marks projects that are already in the instance, so their button shows "Installed". */
    private void refreshInstalled() {
        final ContentType type = mType;
        PojavApplication.sExecutorService.execute(() -> {
            Set<String> ids;
            try {
                List<InstalledContent> contents = InstalledContent.scan(mTarget.getFolder(type), type);
                List<String> hashes = new ArrayList<>(contents.size());
                for(InstalledContent content : contents) hashes.add(content.sha1);
                Map<String, ModrinthModels.Version> versions = mClient.identify(hashes);
                ids = new HashSet<>();
                for(ModrinthModels.Version version : versions.values()) if(version != null) ids.add(version.projectId);
            }catch (IOException e) {
                ids = Collections.emptySet();
            }
            final Set<String> installed = ids;
            Tools.runOnUiThread(() -> {
                if(!isAdded() || getView() == null || type != mType) return;
                mInstalledProjects.clear();
                mInstalledProjects.addAll(installed);
                mAdapter.notifyDataSetChanged();
            });
        });
    }

    private void loadPage(boolean reset) {
        if(mType == ContentType.MOD && !mTarget.supportsMods()) {
            mHits.clear();
            mAdapter.notifyDataSetChanged();
            showStatus(R.string.content_no_loader);
            return;
        }
        if(reset) {
            mSearchGeneration++;
            mHits.clear();
            mTotalHits = 0;
            mAdapter.notifyDataSetChanged();
        }
        final int generation = mSearchGeneration;
        final int offset = mHits.size();
        final ContentType type = mType;
        final String query = mQuery;
        mLoadingPage = true;
        mStatus.setVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
        PojavApplication.sExecutorService.execute(() -> {
            ModrinthModels.SearchResponse response;
            try {
                response = mClient.search(type, mTarget, query, offset);
            }catch (Exception e) {
                response = null;
            }
            final ModrinthModels.SearchResponse result = response;
            Tools.runOnUiThread(() -> {
                if(!isAdded() || getView() == null || generation != mSearchGeneration) return;
                mLoadingPage = false;
                mProgress.setVisibility(View.GONE);
                if(result == null || result.hits == null) {
                    if(mHits.isEmpty()) showStatus(R.string.content_load_error);
                    return;
                }
                mTotalHits = result.totalHits;
                int start = mHits.size();
                Collections.addAll(mHits, result.hits);
                mAdapter.notifyItemRangeInserted(start, result.hits.length);
                if(mHits.isEmpty()) showStatus(R.string.content_no_results);
            });
        });
    }

    private void showStatus(int text) {
        mProgress.setVisibility(View.GONE);
        mStatus.setText(text);
        mStatus.setVisibility(View.VISIBLE);
    }

    private void chooseVersion(ModrinthModels.SearchHit hit) {
        final ContentType type = mType;
        mProgress.setVisibility(View.VISIBLE);
        PojavApplication.sExecutorService.execute(() -> {
            ModrinthModels.Version[] versions;
            try {
                versions = mClient.getCompatibleVersions(hit.projectId, type, mTarget);
            }catch (Exception e) {
                versions = null;
            }
            final ModrinthModels.Version[] result = versions;
            Tools.runOnUiThread(() -> {
                if(!isAdded() || getView() == null) return;
                mProgress.setVisibility(View.GONE);
                if(result == null) {
                    Toast.makeText(requireContext(), R.string.content_load_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                if(result.length == 0) {
                    Toast.makeText(requireContext(), R.string.content_no_compatible, Toast.LENGTH_LONG).show();
                    return;
                }
                String[] labels = new String[result.length];
                for(int i = 0; i < result.length; i++) labels[i] = versionLabel(result[i]);
                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.content_versions_title, hit.title))
                        .setItems(labels, (d, which) -> install(hit, result[which]))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    private static String versionLabel(ModrinthModels.Version version) {
        StringBuilder label = new StringBuilder(version.versionNumber != null ? version.versionNumber : version.name);
        if(version.versionType != null && !"release".equals(version.versionType)) label.append(" (").append(version.versionType).append(')');
        if(version.loaders != null && version.loaders.length > 0) label.append(" · ").append(android.text.TextUtils.join(", ", version.loaders));
        return label.toString();
    }

    private void install(ModrinthModels.SearchHit hit, @Nullable ModrinthModels.Version version) {
        if(ProgressKeeper.getTaskCount() != 0 || !mInstallingProjects.isEmpty()) {
            Toast.makeText(requireContext(), R.string.content_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        final ContentType type = mType;
        final Set<String> installed = new HashSet<>(mInstalledProjects);
        mInstallingProjects.add(hit.projectId);
        mAdapter.notifyDataSetChanged();
        PojavApplication.sExecutorService.execute(() -> {
            List<ModrinthModels.Version> versions = null;
            Exception error = null;
            try {
                ContentInstaller installer = new ContentInstaller(mClient, mTarget);
                versions = version != null
                        ? installer.installVersion(version, type, installed)
                        : installer.install(hit.projectId, type, installed);
            }catch (Exception e) {
                error = e;
            }
            final List<ModrinthModels.Version> done = versions;
            final Exception failure = error;
            Tools.runOnUiThread(() -> {
                mInstallingProjects.remove(hit.projectId);
                if(!isAdded() || getView() == null) return;
                if(done != null) {
                    for(ModrinthModels.Version installedVersion : done) mInstalledProjects.add(installedVersion.projectId);
                    int dependencies = done.size() - 1;
                    String message = dependencies > 0
                            ? getString(R.string.content_installed_with_deps, hit.title, dependencies)
                            : getString(R.string.content_installed_toast, hit.title);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                } else if(failure != null && failure.getCause() instanceof ContentInstaller.NoCompatibleVersionException) {
                    Toast.makeText(requireContext(), R.string.content_no_compatible, Toast.LENGTH_LONG).show();
                } else if(failure != null) {
                    Tools.showError(requireContext(), failure);
                }
                mAdapter.notifyDataSetChanged();
            });
        });
    }

    private class BrowserAdapter extends RecyclerView.Adapter<ContentViewHolder> {
        @NonNull
        @Override
        public ContentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_content, parent, false);
            ContentViewHolder holder = new ContentViewHolder(view, mIconCache);
            holder.installButton.setVisibility(View.VISIBLE);
            holder.versionsButton.setVisibility(View.VISIBLE);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull ContentViewHolder holder, int position) {
            ModrinthModels.SearchHit hit = mHits.get(position);
            holder.title.setText(hit.title);
            holder.subtitle.setText(getString(R.string.content_by_author, hit.author,
                    ContentViewHolder.formatCount(hit.downloads)) + "\n" + (hit.description == null ? "" : hit.description));
            holder.loadIcon(hit.projectId, hit.iconUrl);

            boolean installed = mInstalledProjects.contains(hit.projectId);
            boolean installing = mInstallingProjects.contains(hit.projectId);
            holder.installButton.setEnabled(!installed && !installing);
            holder.installButton.setText(installing ? R.string.content_installing
                    : installed ? R.string.content_installed : R.string.content_install);
            holder.installButton.setOnClickListener(v -> install(hit, null));
            holder.versionsButton.setEnabled(!installing);
            holder.versionsButton.setOnClickListener(v -> chooseVersion(hit));
        }

        @Override
        public int getItemCount() {
            return mHits.size();
        }
    }
}
