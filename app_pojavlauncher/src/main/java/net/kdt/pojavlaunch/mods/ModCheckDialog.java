package net.kdt.pojavlaunch.mods;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.util.HashSet;
import java.util.List;

import git.artdeell.mojo.R;

/** Shows what ModCheck found, with a one-tap fix for each problem. */
public final class ModCheckDialog {
    private ModCheckDialog() {}

    public static void show(Activity activity, ContentTarget target, List<ModCheck.Issue> issues, Runnable onPlay, Runnable onCancel) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_mod_check, null);
        ViewGroup list = content.findViewById(R.id.mod_check_list);
        for(ModCheck.Issue issue : issues) {
            View row = LayoutInflater.from(activity).inflate(R.layout.item_mod_issue, list, false);
            ((TextView) row.findViewById(R.id.issue_title)).setText(title(activity, issue));
            ((TextView) row.findViewById(R.id.issue_detail)).setText(detail(activity, issue));
            Button action = row.findViewById(R.id.issue_action);
            setupAction(activity, target, issue, action);
            list.addView(row);
        }
        final boolean[] played = {false};
        new AlertDialog.Builder(activity, R.style.Voxelith_Dialog)
                .setView(content)
                .setPositiveButton(R.string.mod_check_play, (d, w) -> {
                    played[0] = true;
                    onPlay.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> {
                    if(!played[0]) onCancel.run();
                })
                .show();
    }

    private static String title(Activity activity, ModCheck.Issue issue) {
        switch (issue.type) {
            case ModCheck.WRONG_LOADER: return activity.getString(R.string.mod_check_wrong_loader, issue.subject);
            case ModCheck.DUPLICATE: return activity.getString(R.string.mod_check_duplicate, issue.subject);
            case ModCheck.MISSING_DEPENDENCY: return activity.getString(R.string.mod_check_missing, issue.subject);
            case ModCheck.DISABLED_DEPENDENCY: return activity.getString(R.string.mod_check_disabled, issue.subject);
            case ModCheck.WRONG_GAME_VERSION: return activity.getString(R.string.mod_check_wrong_version, issue.subject);
            default: return activity.getString(R.string.mod_check_conflict, issue.subject, issue.other);
        }
    }

    private static String detail(Activity activity, ModCheck.Issue issue) {
        switch (issue.type) {
            case ModCheck.WRONG_LOADER: return activity.getString(R.string.mod_check_wrong_loader_detail, issue.other);
            case ModCheck.DUPLICATE: return activity.getString(R.string.mod_check_duplicate_detail, issue.other);
            case ModCheck.MISSING_DEPENDENCY: return activity.getString(R.string.mod_check_missing_detail, issue.other);
            case ModCheck.DISABLED_DEPENDENCY: return activity.getString(R.string.mod_check_missing_detail, issue.other);
            case ModCheck.WRONG_GAME_VERSION: return activity.getString(R.string.mod_check_wrong_version_detail, issue.other);
            default: return activity.getString(R.string.mod_check_conflict_detail);
        }
    }

    private static void setupAction(Activity activity, ContentTarget target, ModCheck.Issue issue, Button action) {
        if(issue.toInstall != null) {
            action.setText(R.string.mod_check_install);
            action.setOnClickListener(v -> {
                action.setEnabled(false);
                action.setText(R.string.mod_check_installing);
                PojavApplication.sExecutorService.execute(() -> {
                    boolean ok;
                    try {
                        new ContentInstaller(new ModrinthClient(), target).install(issue.toInstall, ContentType.MOD, new HashSet<>());
                        ok = true;
                    }catch (Exception e) {
                        ok = false;
                    }
                    final boolean success = ok;
                    Tools.runOnUiThread(() -> {
                        if(success) {
                            action.setText(R.string.mod_check_done);
                        }else {
                            action.setEnabled(true);
                            action.setText(R.string.mod_check_install);
                            Toast.makeText(activity, activity.getString(R.string.mod_check_install_failed, issue.subject), Toast.LENGTH_LONG).show();
                        }
                    });
                });
            });
            return;
        }
        if(issue.toEnable != null) {
            action.setText(R.string.mod_check_enable);
            action.setOnClickListener(v -> {
                if(ModMetadata.setEnabled(issue.toEnable, true)) markDone(action);
            });
            return;
        }
        action.setText(issue.toDisable.size() > 1 ? R.string.mod_check_disable_copies : R.string.mod_check_disable);
        action.setOnClickListener(v -> {
            boolean all = true;
            for(File file : issue.toDisable) all &= ModMetadata.setEnabled(file, false);
            if(all) markDone(action);
        });
    }

    private static void markDone(Button action) {
        action.setEnabled(false);
        action.setText(R.string.mod_check_done);
    }
}
