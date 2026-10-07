package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.mods.ContentViewHolder;
import net.kdt.pojavlaunch.mods.ModrinthClient;
import net.kdt.pojavlaunch.mods.ModrinthModels;
import net.kdt.pojavlaunch.mods.PerformancePack;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import git.artdeell.mojo.R;

/** Modpacks from Modrinth, installed as a new instance, with a hint of how heavy each one is for a phone. */
public class ModpacksFragment extends Fragment {
    public static final String TAG = "ModpacksFragment";
    // Up to this many mods most packs run on a 4 GB phone; above the second limit it gets hard
    private static final int LIGHT_MODS = 40;
    private static final int HEAVY_MODS = 120;

    private final ModrinthClient mClient = new ModrinthClient();
    private final List<ModrinthModels.SearchHit> mHits = new ArrayList<>();
    private final ModpackAdapter mAdapter = new ModpackAdapter();
    private ModIconCache mIconCache;
    private EditText mSearch;
    private CheckBox mLightOnly;
    private ProgressBar mProgress;
    private TextView mStatus;
    private String mQuery = "";
    private int mGeneration;
    private int mTotalHits;
    private boolean mLoading;

    public ModpacksFragment() {
        super(R.layout.fragment_modpacks);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mIconCache = ContentViewHolder.sharedIconCache();
        mSearch = view.findViewById(R.id.modpacks_search);
        mLightOnly = view.findViewById(R.id.modpacks_light);
        mProgress = view.findViewById(R.id.modpacks_progress);
        mStatus = view.findViewById(R.id.modpacks_status);
        RecyclerView list = view.findViewById(R.id.modpacks_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        list.setLayoutManager(layoutManager);
        list.setAdapter(mAdapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if(dy <= 0 || mLoading || mHits.size() >= mTotalHits) return;
                if(layoutManager.findLastVisibleItemPosition() >= mHits.size() - 4) load(false);
            }
        });
        mSearch.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if(actionId != EditorInfo.IME_ACTION_SEARCH && !enter) return false;
            mQuery = mSearch.getText().toString().trim();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm != null) imm.hideSoftInputFromWindow(mSearch.getWindowToken(), 0);
            load(true);
            return true;
        });
        mLightOnly.setOnCheckedChangeListener((button, checked) -> load(true));
        load(true);
    }

    private void load(boolean reset) {
        if(reset) {
            mGeneration++;
            mHits.clear();
            mTotalHits = 0;
            mAdapter.notifyDataSetChanged();
        }
        final int generation = mGeneration;
        final int offset = mHits.size();
        final String query = mQuery;
        final boolean lightOnly = mLightOnly.isChecked();
        mLoading = true;
        mStatus.setVisibility(View.GONE);
        mProgress.setVisibility(View.VISIBLE);
        PojavApplication.sExecutorService.execute(() -> {
            ModrinthModels.SearchResponse response;
            try {
                response = mClient.searchModpacks(query, offset, lightOnly);
            }catch (IOException e) {
                response = null;
            }
            final ModrinthModels.SearchResponse result = response;
            Tools.runOnUiThread(() -> {
                if(!isAdded() || getView() == null || generation != mGeneration) return;
                mLoading = false;
                mProgress.setVisibility(View.GONE);
                if(result == null || result.hits == null) {
                    if(mHits.isEmpty()) showStatus(R.string.modpacks_error);
                    return;
                }
                int start = mHits.size();
                mHits.addAll(Arrays.asList(result.hits));
                mTotalHits = result.totalHits;
                mAdapter.notifyItemRangeInserted(start, result.hits.length);
                if(mHits.isEmpty()) showStatus(R.string.modpacks_empty);
            });
        });
    }

    private void showStatus(int text) {
        mStatus.setText(text);
        mStatus.setVisibility(View.VISIBLE);
    }

    private static boolean isTaggedLight(ModrinthModels.SearchHit hit) {
        if(hit.categories == null) return false;
        for(String category : hit.categories) {
            if("optimization".equals(category) || "lightweight".equals(category)) return true;
        }
        return false;
    }

    private void openInstall(ModrinthModels.SearchHit hit) {
        if(ProgressKeeper.getTaskCount() != 0) {
            Toast.makeText(requireContext(), R.string.content_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), R.string.modpacks_loading_versions, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            ModrinthModels.Version[] versions;
            try {
                versions = mClient.getAllVersions(hit.projectId);
            }catch (IOException e) {
                versions = null;
            }
            final ModrinthModels.Version[] result = versions;
            Tools.runOnUiThread(() -> {
                if(!isAdded()) return;
                if(result == null || result.length == 0) {
                    Toast.makeText(requireContext(), R.string.modpacks_error, Toast.LENGTH_LONG).show();
                    return;
                }
                showInstallDialog(hit, result);
            });
        });
    }

    private void showInstallDialog(ModrinthModels.SearchHit hit, ModrinthModels.Version[] versions) {
        Context context = requireContext();
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_modpack_install, null);
        ((TextView) content.findViewById(R.id.modpack_title)).setText(hit.title);
        ((TextView) content.findViewById(R.id.modpack_description)).setText(hit.description);
        Spinner spinner = content.findViewById(R.id.modpack_version);
        TextView weight = content.findViewById(R.id.modpack_weight);
        CheckBox lightOptions = content.findViewById(R.id.modpack_light_options);

        String[] labels = new String[versions.length];
        for(int i = 0; i < versions.length; i++) {
            ModrinthModels.Version version = versions[i];
            String game = version.gameVersions != null && version.gameVersions.length > 0 ? version.gameVersions[0] : "?";
            String loader = version.loaders != null && version.loaders.length > 0 ? version.loaders[0] : "";
            labels[i] = version.name + " · " + game + (loader.isEmpty() ? "" : " · " + loader);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showWeight(weight, versions[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        showWeight(weight, versions[0]);

        new AlertDialog.Builder(context)
                .setView(content)
                .setPositiveButton(R.string.content_install, (d, w) ->
                        install(hit, versions, spinner.getSelectedItemPosition(), lightOptions.isChecked()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showWeight(TextView view, ModrinthModels.Version version) {
        int mods = version.dependencies == null ? 0 : version.dependencies.length;
        ModrinthModels.VersionFile file = version.primaryFile();
        String size = file == null ? "?" : Formatter.formatShortFileSize(view.getContext(), file.size);
        int level;
        int color;
        if(mods == 0) {
            level = R.string.modpacks_weight_unknown;
            color = R.color.text_default;
        }else if(mods <= LIGHT_MODS) {
            level = R.string.modpacks_weight_light;
            color = R.color.voxelith_cyan_light;
        }else if(mods <= HEAVY_MODS) {
            level = R.string.modpacks_weight_medium;
            color = R.color.text_default;
        }else {
            level = R.string.modpacks_weight_heavy;
            color = R.color.modpack_heavy;
        }
        String mod = mods == 0 ? "?" : String.valueOf(mods);
        view.setText(getString(R.string.modpacks_weight, getString(level), mod, size));
        view.setTextColor(ContextCompat.getColor(view.getContext(), color));
    }

    private void install(ModrinthModels.SearchHit hit, ModrinthModels.Version[] versions, int index, boolean lightOptions) {
        if(index < 0 || index >= versions.length) return;
        String[] names = new String[versions.length];
        String[] games = new String[versions.length];
        String[] urls = new String[versions.length];
        String[] hashes = new String[versions.length];
        for(int i = 0; i < versions.length; i++) {
            ModrinthModels.Version version = versions[i];
            ModrinthModels.VersionFile file = version.primaryFile();
            names[i] = version.name;
            games[i] = version.gameVersions != null && version.gameVersions.length > 0 ? version.gameVersions[0] : "";
            urls[i] = file == null ? null : file.url;
            hashes[i] = file == null || file.hashes == null ? null : file.hashes.sha1;
        }
        if(urls[index] == null) {
            Toast.makeText(requireContext(), R.string.modpacks_error, Toast.LENGTH_LONG).show();
            return;
        }
        ModItem item = new ModItem(Constants.SOURCE_MODRINTH, true, hit.projectId, hit.title, hit.description, hit.iconUrl);
        ModDetail detail = new ModDetail(item, names, games, urls, hashes);
        reuseIcon(hit.projectId, item.getIconCacheTag());
        Toast.makeText(requireContext(), getString(R.string.modpacks_installing, hit.title), Toast.LENGTH_SHORT).show();
        final Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                new ModrinthApi().installModpack(detail, index);
                if(lightOptions) {
                    Instance instance = Instances.loadSelectedInstance();
                    if(instance != null) PerformancePack.applyLightOptions(instance.getGameDirectory());
                }
                Tools.runOnUiThread(() -> Toast.makeText(appContext, appContext.getString(R.string.modpacks_installed, hit.title), Toast.LENGTH_LONG).show());
            }catch (Exception e) {
                Tools.showErrorRemote(e);
            }
        });
    }

    /** The browser already cached the icon under its own tag; the installer looks for the old one. */
    private static void reuseIcon(String projectId, String installerTag) {
        File cached = new File(Tools.DIR_CACHE, "mod_icons/vx_" + projectId + ".ca");
        File target = new File(Tools.DIR_CACHE, "mod_icons/" + installerTag + ".ca");
        if(!cached.isFile() || target.isFile()) return;
        try {
            FileUtils.copyFile(cached, target);
        }catch (IOException ignored) {}
    }

    private class ModpackAdapter extends RecyclerView.Adapter<ContentViewHolder> {
        @NonNull
        @Override
        public ContentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_content, parent, false);
            ContentViewHolder holder = new ContentViewHolder(view, mIconCache);
            holder.installButton.setVisibility(View.VISIBLE);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull ContentViewHolder holder, int position) {
            ModrinthModels.SearchHit hit = mHits.get(position);
            holder.title.setText(hit.title);
            String meta = getString(R.string.content_by_author, hit.author, ContentViewHolder.formatCount(hit.downloads));
            if(isTaggedLight(hit)) meta = getString(R.string.modpacks_light_tag) + " · " + meta;
            holder.subtitle.setText(meta + "\n" + (hit.description == null ? "" : hit.description));
            holder.loadIcon(hit.projectId, hit.iconUrl);
            holder.installButton.setText(R.string.content_install);
            holder.installButton.setOnClickListener(v -> openInstall(hit));
            holder.itemView.setOnClickListener(v -> openInstall(hit));
        }

        @Override
        public int getItemCount() {
            return mHits.size();
        }
    }
}
