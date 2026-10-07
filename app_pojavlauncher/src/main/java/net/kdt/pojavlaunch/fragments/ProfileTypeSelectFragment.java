package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.InstanceBackup;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import java.io.IOException;
import java.io.InputStream;

public class ProfileTypeSelectFragment extends Fragment {
    public static final String TAG = "ProfileTypeSelectFragment";
    private final ActivityResultLauncher<String[]> mImportLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importBackup);
    private boolean mImporting;

    public ProfileTypeSelectFragment() {
        super(R.layout.fragment_profile_type);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.vanilla_profile).setOnClickListener(v -> {
            try {
                Instance instance = Instances.createDefaultInstance();
                Instances.setSelectedInstance(instance);
                Tools.swapFragment(requireActivity(), InstanceEditorFragment.class,
                        InstanceEditorFragment.TAG, new Bundle(1));
            }catch (IOException e) {
                Tools.showError(view.getContext(), e);
            }
        });

        // NOTE: Special care needed! If you wll decide to add these to the back stack, please read
        // the comment in FabricInstallFragment.onDownloadFinished() and amend the code
        // in FabricInstallFragment.onDownloadFinished() and ModVersionListFragment.onDownloadFinished()
        view.findViewById(R.id.optifine_profile).setOnClickListener(v -> Tools.swapFragment(requireActivity(), OptiFineInstallFragment.class,
                OptiFineInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_fabric).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), FabricInstallFragment.class, FabricInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_forge).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), ForgeInstallFragment.class, ForgeInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_modpack).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), ModpacksFragment.class, ModpacksFragment.TAG, null));
        view.findViewById(R.id.import_instance_backup).setOnClickListener(v ->
                mImportLauncher.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"}));
        view.findViewById(R.id.modded_profile_quilt).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), QuiltInstallFragment.class, QuiltInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_bta).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), BTAInstallFragment.class, BTAInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_neoforge).setOnClickListener((v)->
                Tools.swapFragment(requireActivity(), NeoforgeInstallFragment.class, NeoforgeInstallFragment.TAG, null));
        view.findViewById(R.id.modded_profile_legacy_fabric).setOnClickListener((v) ->
                Tools.swapFragment(requireActivity(), LegacyFabricInstallFragment.class, LegacyFabricInstallFragment.TAG, null));
    }

    private void importBackup(@Nullable Uri uri) {
        if(uri == null || mImporting) return;
        mImporting = true;
        Context context = requireContext().getApplicationContext();
        Toast.makeText(context, R.string.instance_backup_importing, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            String message;
            boolean ok = false;
            try(InputStream in = context.getContentResolver().openInputStream(uri)) {
                if(in == null) throw new IOException("empty file");
                Instance instance = InstanceBackup.importBackup(in);
                message = context.getString(R.string.instance_backup_imported, instance.name);
                ok = true;
            }catch (InstanceBackup.NotABackupException e) {
                message = context.getString(R.string.instance_backup_invalid);
            }catch (Exception e) {
                message = context.getString(R.string.worlds_error, e.getMessage());
            }
            final String result = message;
            final boolean success = ok;
            Tools.runOnUiThread(() -> {
                mImporting = false;
                Toast.makeText(context, result, Toast.LENGTH_LONG).show();
                if(success && isAdded()) Tools.backToMainMenu(requireActivity());
            });
        });
    }
}
