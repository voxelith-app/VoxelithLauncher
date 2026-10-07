package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ImageReceiver;
import net.kdt.pojavlaunch.mods.ContentFragments;
import net.kdt.pojavlaunch.mods.ContentInstaller;
import net.kdt.pojavlaunch.mods.ContentTarget;
import net.kdt.pojavlaunch.mods.ContentType;
import net.kdt.pojavlaunch.mods.ContentViewHolder;
import net.kdt.pojavlaunch.mods.GalleryImageLoader;
import net.kdt.pojavlaunch.mods.InstalledContent;
import net.kdt.pojavlaunch.mods.ModrinthClient;
import net.kdt.pojavlaunch.mods.ModrinthModels;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import git.artdeell.mojo.R;

/** Description, screenshots and changelog of a Modrinth project, with install buttons. */
public class ContentDetailsFragment extends Fragment {
    public static final String TAG = "ContentDetailsFragment";
    private static final String ARG_PROJECT = "project_id";
    private static final int MAX_GALLERY_IMAGES = 4;

    private final ModrinthClient mClient = new ModrinthClient();
    private ContentType mType;
    private ContentTarget mTarget;
    private String mProjectId;
    private String mTitle = "";
    private ModrinthModels.Version[] mVersions = new ModrinthModels.Version[0];
    private final Set<String> mInstalledProjects = new HashSet<>();
    private boolean mInstalling;
    private Button mInstallButton;
    private ProgressBar mProgress;

    public ContentDetailsFragment() {
        super(R.layout.fragment_content_details);
    }

    public static Bundle args(String projectId, ContentType type) {
        Bundle bundle = ContentFragments.args(type);
        bundle.putString(ARG_PROJECT, projectId);
        return bundle;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Instance instance = Instances.loadSelectedInstance();
        mProjectId = getArguments() == null ? null : getArguments().getString(ARG_PROJECT);
        if(instance == null || mProjectId == null) {
            Tools.removeCurrentFragment(requireActivity());
            return;
        }
        mType = ContentFragments.typeFrom(getArguments());
        mTarget = ContentTarget.fromInstance(instance);
        mInstallButton = view.findViewById(R.id.details_install);
        mProgress = view.findViewById(R.id.details_progress);
        mInstallButton.setOnClickListener(v -> install(mVersions.length > 0 ? mVersions[0] : null));
        view.findViewById(R.id.details_versions).setOnClickListener(v -> chooseVersion());
        load(view);
    }

    private void load(View view) {
        final ContentType type = mType;
        PojavApplication.sExecutorService.execute(() -> {
            ModrinthModels.Project project;
            ModrinthModels.Version[] versions;
            ModrinthModels.Version latest = null;
            Set<String> installed = new HashSet<>();
            try {
                project = mClient.getProject(mProjectId);
                versions = mClient.getCompatibleVersions(mProjectId, type, mTarget);
                if(versions.length > 0) latest = mClient.getVersion(versions[0].id);
                List<InstalledContent> contents = InstalledContent.scan(mTarget.getFolder(type), type);
                List<String> hashes = new ArrayList<>(contents.size());
                for(InstalledContent content : contents) hashes.add(content.sha1);
                for(ModrinthModels.Version version : mClient.identify(hashes).values()) {
                    if(version != null) installed.add(version.projectId);
                }
            }catch (Exception e) {
                Tools.runOnUiThread(() -> {
                    if(!isAdded()) return;
                    mProgress.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), R.string.content_load_error, Toast.LENGTH_SHORT).show();
                });
                return;
            }
            final ModrinthModels.Version newest = latest;
            final ModrinthModels.Version[] compatible = versions;
            Tools.runOnUiThread(() -> {
                if(!isAdded() || getView() == null) return;
                mVersions = compatible;
                mInstalledProjects.addAll(installed);
                show(view, project, newest);
            });
        });
    }

    private void show(View view, ModrinthModels.Project project, @Nullable ModrinthModels.Version latest) {
        mProgress.setVisibility(View.GONE);
        mTitle = project.title;
        ((TextView) view.findViewById(R.id.details_title)).setText(project.title);
        ((TextView) view.findViewById(R.id.details_subtitle)).setText(getString(R.string.content_target_downloads,
                ContentViewHolder.formatCount(project.downloads)));
        ((TextView) view.findViewById(R.id.details_summary)).setText(project.description);
        ((TextView) view.findViewById(R.id.details_body)).setText(ContentFragments.markdownToText(project.body));

        ImageView icon = view.findViewById(R.id.details_icon);
        icon.setClipToOutline(true);
        if(project.iconUrl != null) {
            ContentViewHolder.sharedIconCache().getImage(new ImageReceiver() {
                @Override
                public void onImageAvailable(android.graphics.Bitmap image) {
                    icon.setImageBitmap(image);
                }
            }, "vx_" + project.id, project.iconUrl);
        }

        if(latest != null && latest.changelog != null && !latest.changelog.trim().isEmpty()) {
            TextView changelogTitle = view.findViewById(R.id.details_changelog_title);
            TextView changelog = view.findViewById(R.id.details_changelog);
            changelogTitle.setText(getString(R.string.content_details_changelog, latest.versionNumber));
            changelog.setText(ContentFragments.markdownToText(latest.changelog));
            changelogTitle.setVisibility(View.VISIBLE);
            changelog.setVisibility(View.VISIBLE);
        }

        showGallery(view, project.gallery);
        updateInstallButton();
    }

    private void showGallery(View view, @Nullable ModrinthModels.GalleryImage[] gallery) {
        if(gallery == null || gallery.length == 0) return;
        LinearLayout container = view.findViewById(R.id.details_gallery);
        int width = getResources().getDimensionPixelSize(R.dimen._180sdp);
        int height = width * 9 / 16;
        int margin = (int) (8 * getResources().getDisplayMetrics().density);
        for(int i = 0; i < gallery.length && i < MAX_GALLERY_IMAGES; i++) {
            ImageView image = new ImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.setMarginEnd(margin);
            image.setLayoutParams(params);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundResource(R.drawable.background_icon_round);
            image.setClipToOutline(true);
            container.addView(image);
            GalleryImageLoader.load(image, gallery[i].url, width);
        }
        view.findViewById(R.id.details_gallery_scroll).setVisibility(View.VISIBLE);
    }

    private void updateInstallButton() {
        boolean installed = mInstalledProjects.contains(mProjectId);
        if(mInstalling) mInstallButton.setText(R.string.content_installing);
        else if(installed) mInstallButton.setText(R.string.content_installed);
        else if(mVersions.length == 0) mInstallButton.setText(R.string.content_details_no_compatible);
        else mInstallButton.setText(R.string.content_install);
        mInstallButton.setEnabled(!mInstalling && !installed && mVersions.length > 0);
    }

    private void chooseVersion() {
        if(mVersions.length == 0) {
            Toast.makeText(requireContext(), R.string.content_no_compatible, Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[mVersions.length];
        for(int i = 0; i < mVersions.length; i++) labels[i] = ContentFragments.versionLabel(mVersions[i]);
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.content_versions_title, mTitle))
                .setItems(labels, (d, which) -> install(mVersions[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void install(@Nullable ModrinthModels.Version version) {
        if(version == null || mInstalling) return;
        if(ProgressKeeper.getTaskCount() != 0) {
            Toast.makeText(requireContext(), R.string.content_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        final ContentType type = mType;
        final Set<String> installed = new HashSet<>(mInstalledProjects);
        mInstalling = true;
        updateInstallButton();
        PojavApplication.sExecutorService.execute(() -> {
            List<ModrinthModels.Version> done = null;
            Exception failure = null;
            try {
                done = new ContentInstaller(mClient, mTarget).installVersion(version, type, installed);
            }catch (Exception e) {
                failure = e;
            }
            final List<ModrinthModels.Version> result = done;
            final Exception error = failure;
            Tools.runOnUiThread(() -> {
                mInstalling = false;
                if(!isAdded() || getView() == null) return;
                if(result != null) {
                    for(ModrinthModels.Version installedVersion : result) mInstalledProjects.add(installedVersion.projectId);
                    Toast.makeText(requireContext(), getString(R.string.content_installed_toast, mTitle), Toast.LENGTH_SHORT).show();
                } else if(error != null) {
                    Tools.showError(requireContext(), error);
                }
                updateInstallButton();
            });
        });
    }
}
