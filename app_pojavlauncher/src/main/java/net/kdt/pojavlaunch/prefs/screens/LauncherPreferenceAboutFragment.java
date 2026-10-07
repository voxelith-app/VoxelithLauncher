package net.kdt.pojavlaunch.prefs.screens;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.fragments.WelcomeFragment;
import net.kdt.pojavlaunch.utils.UpdateChecker;

import git.artdeell.mojo.R;

public class LauncherPreferenceAboutFragment extends LauncherPreferenceFragment {
    private static final String SOURCE_URL = "https://github.com/voxelith-app/VoxelithLauncher";

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_about);

        String commit = getString(R.string.voxelith_commit);
        String build = commit.isEmpty() ? getString(R.string.about_local_build) : commit.substring(0, Math.min(7, commit.length()));
        requirePreference("aboutVersion").setSummary(getString(R.string.about_version_summary, versionName(), build));
        requirePreference("aboutVersion").setOnPreferenceClickListener(p -> {
            if(!commit.isEmpty()) Tools.openURL(requireActivity(), SOURCE_URL + "/commit/" + commit);
            return true;
        });
        requirePreference("aboutCheckUpdate").setOnPreferenceClickListener(p -> {
            UpdateChecker.checkNow(requireActivity());
            return true;
        });
        requirePreference("aboutWelcome").setOnPreferenceClickListener(p -> {
            Tools.swapFragment(requireActivity(), WelcomeFragment.class, WelcomeFragment.TAG, null);
            return true;
        });
        link("aboutSource", SOURCE_URL);
        link("aboutPcLauncher", "https://github.com/voxelith-app/code");
        link("aboutLicense", SOURCE_URL + "#licen%C3%A7a");
    }

    private void link(String key, String url) {
        requirePreference(key).setOnPreferenceClickListener(p -> {
            Tools.openURL(requireActivity(), url);
            return true;
        });
    }

    private String versionName() {
        try {
            PackageInfo info = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0);
            return info.versionName;
        }catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}
