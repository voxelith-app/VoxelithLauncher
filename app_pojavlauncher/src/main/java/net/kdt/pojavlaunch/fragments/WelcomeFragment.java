package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.perf.MemoryAdvisor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import git.artdeell.mojo.R;

/** Short first run guide: account, first instance and where the in-game menu is. */
public class WelcomeFragment extends Fragment {
    public static final String TAG = "WelcomeFragment";
    public static final String PREF_DONE = "voxelithWelcomeDone";
    private static final int STEPS = 4;
    private static final String STATE_STEP = "step";

    private int mStep;
    private TextView mStepText;
    private TextView mTitle;
    private TextView mBody;
    private Button mAction;
    private Button mAction2;
    private Button mNext;

    public WelcomeFragment() {
        super(R.layout.fragment_welcome);
    }

    public static boolean isDone() {
        return LauncherPreferences.DEFAULT_PREF.getBoolean(PREF_DONE, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if(savedInstanceState != null) mStep = savedInstanceState.getInt(STATE_STEP);
        mStepText = view.findViewById(R.id.welcome_step);
        mTitle = view.findViewById(R.id.welcome_title);
        mBody = view.findViewById(R.id.welcome_body);
        mAction = view.findViewById(R.id.welcome_action);
        mAction2 = view.findViewById(R.id.welcome_action2);
        mNext = view.findViewById(R.id.welcome_next);
        mNext.setOnClickListener(v -> {
            if(mStep == STEPS - 1) finish();
            else {
                mStep++;
                showStep();
            }
        });
        view.findViewById(R.id.welcome_skip).setOnClickListener(v -> finish());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, mStep);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from the login or instance screens updates what the step says
        showStep();
    }

    private void showStep() {
        mStepText.setText(getString(R.string.welcome_step, mStep + 1, STEPS));
        mNext.setText(mStep == STEPS - 1 ? R.string.welcome_finish : R.string.welcome_next);
        mAction.setVisibility(View.GONE);
        mAction2.setVisibility(View.GONE);
        switch (mStep) {
            case 0:
                mTitle.setText(R.string.welcome_title_intro);
                mBody.setText(R.string.welcome_body_intro);
                break;
            case 1: {
                mTitle.setText(R.string.welcome_title_account);
                Account account = Accounts.getCurrent();
                mBody.setText(account == null
                        ? getString(R.string.welcome_body_account_none)
                        : getString(R.string.welcome_body_account, account.username));
                setAction(mAction, account == null ? R.string.welcome_add_account : R.string.welcome_other_account,
                        () -> Tools.swapFragment(requireActivity(), SelectAuthFragment.class, SelectAuthFragment.TAG, null));
                break;
            }
            case 2: {
                mTitle.setText(R.string.welcome_title_instance);
                mBody.setText(R.string.welcome_body_instance_loading);
                final Context context = requireContext().getApplicationContext();
                PojavApplication.sExecutorService.execute(() -> {
                    Instance instance = Instances.loadSelectedInstance();
                    String text = instance == null
                            ? context.getString(R.string.welcome_body_instance_none)
                            : context.getString(R.string.welcome_body_instance, instance.name,
                                MemoryAdvisor.recommend(context, instance));
                    Tools.runOnUiThread(() -> {
                        if(isAdded() && mStep == 2) mBody.setText(text);
                    });
                });
                setAction(mAction, R.string.welcome_create_instance,
                        () -> Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));
                setAction(mAction2, R.string.welcome_light_modpacks,
                        () -> Tools.swapFragment(requireActivity(), ModpacksFragment.class, ModpacksFragment.TAG, null));
                break;
            }
            default:
                mTitle.setText(R.string.welcome_title_controls);
                mBody.setText(R.string.welcome_body_controls);
                setAction(mAction, R.string.welcome_edit_controls,
                        () -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
                break;
        }
    }

    private static void setAction(Button button, int text, Runnable action) {
        button.setText(text);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> action.run());
    }

    private void finish() {
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_DONE, true).apply();
        Tools.backToMainMenu(requireActivity());
    }
}
