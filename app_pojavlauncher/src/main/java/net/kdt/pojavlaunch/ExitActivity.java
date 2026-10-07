package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Tools.shareLog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.kdt.mcgui.MineButton;

import net.kdt.pojavlaunch.crash.CrashAnalyzer;
import net.kdt.pojavlaunch.mods.ModMetadata;
import net.kdt.pojavlaunch.stats.PlayTime;

import java.io.File;

import git.artdeell.mojo.R;

@Keep
public class ExitActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN = "voxelith_open";
    public static final String OPEN_MODS = "mods";
    public static final String OPEN_VIDEO = "video";
    public static final String OPEN_JAVA = "java";

    private AlertDialog mDialog;

    @SuppressLint("StringFormatInvalid") //invalid on some translations but valid on most, cant fix that atm
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int code = -1;
        boolean isSignal = false;
        String gameDirectory = null;
        long sessionStart = 0;
        Bundle extras = getIntent().getExtras();
        if(extras != null) {
            code = extras.getInt("code",-1);
            isSignal = extras.getBoolean("isSignal", false);
            gameDirectory = extras.getString("gameDirectory");
            sessionStart = extras.getLong("sessionStart", 0);
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_crash, null);
        TextView title = content.findViewById(R.id.crash_title);
        title.setText(isSignal ? getString(R.string.mcn_abort_title) : getString(R.string.mcn_exit_title, code));

        mDialog = new AlertDialog.Builder(this, R.style.Voxelith_Dialog)
                .setView(content)
                .setNeutralButton(R.string.main_share_logs, (dialog, which) -> shareLog(this))
                .setNegativeButton(R.string.crash_close, null)
                .setPositiveButton(R.string.crash_open_mods, null)
                .setOnDismissListener(dialog -> ExitActivity.this.finish())
                .show();
        // Only shown once the analysis knows where to send the player
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);

        final File gameDir = gameDirectory != null ? new File(gameDirectory) : null;
        final long start = sessionStart;
        final boolean killed = isSignal;
        PojavApplication.sExecutorService.execute(() -> {
            CrashAnalyzer.Result result = CrashAnalyzer.analyze(gameDir, start, killed);
            Tools.runOnUiThread(() -> showResult(content, result));
        });
    }

    private void showResult(View content, CrashAnalyzer.Result result) {
        if(isFinishing() || mDialog == null || !mDialog.isShowing()) return;
        if(result.kind == CrashAnalyzer.KILLED) {
            ((TextView) content.findViewById(R.id.crash_title)).setText(R.string.crash_title_killed);
        }
        ((TextView) content.findViewById(R.id.crash_message)).setText(message(result));

        TextView evidence = content.findViewById(R.id.crash_evidence);
        if(result.evidence != null) {
            evidence.setText(result.evidence);
            evidence.setVisibility(View.VISIBLE);
        }

        LinearLayout culprits = content.findViewById(R.id.crash_culprits);
        for(ModMetadata mod : result.culprits) {
            if(culprits.getChildCount() >= 3) break;
            MineButton button = new MineButton(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) (44 * getResources().getDisplayMetrics().density));
            params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
            button.setLayoutParams(params);
            button.setText(getString(R.string.crash_disable, mod.displayName()));
            button.setOnClickListener(v -> {
                if(ModMetadata.setEnabled(mod.file, false)) {
                    button.setText(getString(R.string.crash_disabled, mod.displayName()));
                    button.setEnabled(false);
                }
            });
            culprits.addView(button);
        }

        String open = null;
        int label = 0;
        switch (result.action) {
            case CrashAnalyzer.ACTION_MODS: open = OPEN_MODS; label = R.string.crash_open_mods; break;
            case CrashAnalyzer.ACTION_VIDEO: open = OPEN_VIDEO; label = R.string.crash_open_video; break;
            case CrashAnalyzer.ACTION_JAVA: open = OPEN_JAVA; label = R.string.crash_open_java; break;
        }
        if(open != null) {
            final String target = open;
            Button positive = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if(positive == null) return;
            positive.setText(label);
            positive.setVisibility(View.VISIBLE);
            positive.setOnClickListener(v -> {
                Intent intent = new Intent(this, LauncherActivity.class);
                intent.putExtra(EXTRA_OPEN, target);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                mDialog.dismiss();
            });
        }
    }

    private String message(CrashAnalyzer.Result result) {
        switch (result.kind) {
            case CrashAnalyzer.OUT_OF_MEMORY: return getString(R.string.crash_oom);
            case CrashAnalyzer.WRONG_JAVA: return getString(R.string.crash_wrong_java, result.detail);
            case CrashAnalyzer.MISSING_DEPENDENCY: return getString(R.string.crash_missing, result.detail);
            case CrashAnalyzer.DUPLICATE_MOD: return getString(R.string.crash_duplicate);
            case CrashAnalyzer.MOD_ERROR: return getString(R.string.crash_mod_error);
            case CrashAnalyzer.RENDERER: return getString(R.string.crash_renderer);
            case CrashAnalyzer.KILLED: return getString(R.string.crash_killed);
            default: return getString(R.string.crash_unknown);
        }
    }

    @SuppressWarnings("unused") //used by native jre_launcher_new
    public static void showExitMessage(Context ctx, int code, boolean isSignal) {
        PlayTime.Session session = PlayTime.finish();
        if((!isSignal && code == 0)) {
            if(ctx != null) Tools.restartLauncherActivity(ctx);
            System.exit(0);
            return;
        }

        Object lock = new Object();
        Tools.runOnUiThread(()->{
            Intent i = new Intent(ctx,ExitActivity.class);
            i.putExtra("code",code);
            i.putExtra("isSignal", isSignal);
            if(session != null) {
                i.putExtra("gameDirectory", session.gameDirectory);
                i.putExtra("sessionStart", session.start);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            synchronized (lock) {
                lock.notify();
            }
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                Log.e("ExitActivity", "Waiting on lock failed: "+e);
            }
        }
        System.exit(0);
    }

}
