package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.AuthType;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.mods.ContentInstaller;
import net.kdt.pojavlaunch.mods.ContentTarget;
import net.kdt.pojavlaunch.mods.ContentType;
import net.kdt.pojavlaunch.mods.ModrinthClient;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.skins.SkinPreviewRenderer;
import net.kdt.pojavlaunch.skins.SkinService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

import git.artdeell.mojo.R;

/** Skin of the current account: Microsoft through the official API, Ely.by through the site, local accounts on the device. */
public class SkinsFragment extends Fragment {
    public static final String TAG = "SkinsFragment";
    private static final int MAX_SKIN_BYTES = 1024 * 1024;

    private final SkinPreviewRenderer mRenderer = new SkinPreviewRenderer();
    private Account mAccount;
    private Bitmap mSkin;
    private boolean mBack;
    private boolean mBusy;
    private int mGeneration;

    private ImageView mPreview;
    private ProgressBar mLoading;
    private TextView mState;
    private RadioGroup mModel;

    private final ActivityResultLauncher<String> mPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::onSkinPicked);

    public SkinsFragment() {
        super(R.layout.fragment_skins);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mPreview = view.findViewById(R.id.skins_preview);
        mLoading = view.findViewById(R.id.skins_loading);
        mState = view.findViewById(R.id.skins_state);
        mModel = view.findViewById(R.id.skins_model);
        TextView accountText = view.findViewById(R.id.skins_account);
        TextView info = view.findViewById(R.id.skins_info);
        Button upload = view.findViewById(R.id.skins_upload);
        Button loader = view.findViewById(R.id.skins_loader);
        Button site = view.findViewById(R.id.skins_site);
        Button reset = view.findViewById(R.id.skins_reset);

        mAccount = Accounts.getCurrent();
        if(mAccount == null) {
            accountText.setText(R.string.skins_no_account);
            info.setText(R.string.skins_no_account);
            mLoading.setVisibility(View.GONE);
            upload.setEnabled(false);
            reset.setEnabled(false);
            mModel.setEnabled(false);
            return;
        }

        boolean elyBy = mAccount.authType == AuthType.ELY_BY;
        boolean local = mAccount.authType == AuthType.LOCAL || (mAccount.isLocal() && !elyBy);
        accountText.setText(getString(R.string.skins_account, mAccount.username, getString(
                elyBy ? R.string.skins_type_elyby : local ? R.string.skins_type_local : R.string.skins_type_microsoft)));
        info.setText(elyBy ? R.string.skins_info_elyby : local ? R.string.skins_info_local : R.string.skins_info_microsoft);

        view.findViewById(R.id.skins_preview_frame).setOnClickListener(v -> {
            mBack = !mBack;
            showPreview();
        });
        upload.setOnClickListener(v -> {
            if(!mBusy) mPicker.launch("image/png");
        });
        reset.setOnClickListener(v -> reset(local));
        mModel.setOnCheckedChangeListener((group, checkedId) -> onModelChanged());

        if(elyBy) {
            upload.setVisibility(View.GONE);
            reset.setVisibility(View.GONE);
            site.setVisibility(View.VISIBLE);
            site.setOnClickListener(v -> Tools.openURL(requireActivity(), "https://ely.by"));
            for(int i = 0; i < mModel.getChildCount(); i++) mModel.getChildAt(i).setEnabled(false);
        }
        if(local) {
            loader.setVisibility(View.VISIBLE);
            loader.setOnClickListener(v -> installSkinLoader());
        }
        load();
    }

    private boolean isSlim() {
        return mModel.getCheckedRadioButtonId() == R.id.skins_model_slim;
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        mLoading.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void load() {
        final int generation = ++mGeneration;
        final Account account = mAccount;
        setBusy(true);
        PojavApplication.sExecutorService.execute(() -> {
            SkinService.Skin skin;
            IOException error = null;
            try {
                skin = SkinService.load(account);
            }catch (IOException e) {
                skin = new SkinService.Skin(null, false);
                error = e;
            }
            final SkinService.Skin result = skin;
            final IOException failure = error;
            Tools.runOnUiThread(() -> {
                if(!isAdded() || getView() == null || generation != mGeneration) return;
                setBusy(false);
                mSkin = result.bitmap;
                mModel.setOnCheckedChangeListener(null);
                mModel.check(result.slim ? R.id.skins_model_slim : R.id.skins_model_classic);
                mModel.setOnCheckedChangeListener((group, checkedId) -> onModelChanged());
                if(failure != null) mState.setText(errorText(failure));
                else mState.setText(mSkin == null ? R.string.skins_state_default : R.string.skins_state_custom);
                showPreview();
            });
        });
    }

    private void showPreview() {
        if(mSkin == null) {
            mPreview.setImageDrawable(null);
            return;
        }
        int pixel = Math.max(2, (int) (6 * getResources().getDisplayMetrics().density));
        Bitmap preview = mRenderer.render(mSkin, isSlim(), mBack, pixel);
        mPreview.setImageBitmap(preview);
    }

    private void onModelChanged() {
        showPreview();
        if(mSkin == null || mAccount.authType == AuthType.ELY_BY) return;
        // Changing the model re-applies the current skin with the new arms
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mSkin.compress(Bitmap.CompressFormat.PNG, 100, out);
        apply(out.toByteArray());
    }

    private void onSkinPicked(@Nullable Uri uri) {
        if(uri == null) return;
        Context context = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            try(InputStream in = context.getContentResolver().openInputStream(uri)) {
                if(in == null) throw new IOException("empty file");
                byte[] bytes = readLimited(in);
                Tools.runOnUiThread(() -> {
                    if(isAdded()) apply(bytes);
                });
            }catch (IOException e) {
                Tools.runOnUiThread(() -> {
                    if(isAdded()) Toast.makeText(requireContext(), errorText(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if(out.size() > MAX_SKIN_BYTES) throw new IOException("file too large");
        }
        return out.toByteArray();
    }

    private void apply(byte[] png) {
        final Account account = mAccount;
        final boolean slim = isSlim();
        final boolean local = account.authType == AuthType.LOCAL || account.isLocal();
        setBusy(true);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                SkinService.validate(png).recycle();
                if(local) SkinService.saveLocal(account, png, slim);
                else SkinService.uploadMicrosoft(account, png, slim);
                Tools.runOnUiThread(() -> {
                    if(!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.skins_applied, Toast.LENGTH_SHORT).show();
                    load();
                });
            }catch (IOException e) {
                Tools.runOnUiThread(() -> {
                    if(!isAdded()) return;
                    setBusy(false);
                    Toast.makeText(requireContext(), errorText(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void reset(boolean local) {
        if(mBusy) return;
        final Account account = mAccount;
        setBusy(true);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if(local) SkinService.removeLocal(account);
                else SkinService.resetMicrosoft(account);
                Tools.runOnUiThread(() -> {
                    if(isAdded()) load();
                });
            }catch (IOException e) {
                Tools.runOnUiThread(() -> {
                    if(!isAdded()) return;
                    setBusy(false);
                    Toast.makeText(requireContext(), errorText(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void installSkinLoader() {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        final ContentTarget target = ContentTarget.fromInstance(instance);
        if(!target.supportsMods()) {
            Toast.makeText(requireContext(), R.string.skins_loader_needs_loader, Toast.LENGTH_LONG).show();
            return;
        }
        if(SkinService.hasCustomSkinLoader(target.gameDirectory)) {
            Toast.makeText(requireContext(), R.string.skins_loader_installed, Toast.LENGTH_SHORT).show();
            return;
        }
        if(ProgressKeeper.getTaskCount() != 0) {
            Toast.makeText(requireContext(), R.string.content_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        final Account account = mAccount;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                new ContentInstaller(new ModrinthClient(), target)
                        .install(SkinService.CUSTOM_SKIN_LOADER, ContentType.MOD, new HashSet<>());
                SkinService.syncLocal(account, target.gameDirectory);
                Tools.runOnUiThread(() -> {
                    if(isAdded()) Toast.makeText(requireContext(), R.string.skins_loader_done, Toast.LENGTH_LONG).show();
                });
            }catch (Exception e) {
                Tools.showErrorRemote(e);
            }
        });
    }

    private String errorText(IOException e) {
        if(e instanceof SkinService.InvalidSkinException) {
            SkinService.InvalidSkinException invalid = (SkinService.InvalidSkinException) e;
            return getString(R.string.skins_error_size, invalid.width, invalid.height);
        }
        if(e instanceof SkinService.SessionExpiredException) return getString(R.string.skins_error_session);
        return getString(R.string.skins_error_generic, e.getMessage());
    }
}
