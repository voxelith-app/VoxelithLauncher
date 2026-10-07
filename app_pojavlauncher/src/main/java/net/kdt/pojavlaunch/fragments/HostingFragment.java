package net.kdt.pojavlaunch.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import git.artdeell.mojo.R;

/**
 * Partner tab for BlackHosting. Plans come from a JSON bundled in the app and are refreshed
 * from the repository, so prices can be fixed on GitHub without a new APK.
 */
public class HostingFragment extends Fragment {
    public static final String TAG = "HostingFragment";
    private static final String REMOTE_URL = "https://raw.githubusercontent.com/voxelith-app/VoxelithLauncher/v3_openjdk/hosting/blackhosting.json";
    private static final String ASSET_NAME = "blackhosting.json";

    public HostingFragment() {
        super(R.layout.fragment_hosting);
    }

    static class HostingInfo {
        String name;
        String tagline;
        String url;
        String coupon;
        String couponText;
        Item[] features;
        Plan[] plans;
        Item[] extras;
        Link[] links;
    }

    static class Item {
        String title;
        String text;
    }

    static class Plan {
        String name;
        String cpu;
        String disk;
        String price;
        String url;
        boolean highlight;
    }

    static class Link {
        String label;
        String url;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        HostingInfo info = loadCached();
        if(info == null) info = loadAsset();
        if(info != null) bind(view, info);
        refreshRemote(view);
    }

    @Nullable
    private HostingInfo loadAsset() {
        try(InputStream inputStream = requireContext().getAssets().open(ASSET_NAME)) {
            return new Gson().fromJson(Tools.read(inputStream), HostingInfo.class);
        }catch (Exception e) {
            return null;
        }
    }

    private static File cacheFile() {
        return new File(Tools.DIR_CACHE, ASSET_NAME);
    }

    @Nullable
    private static HostingInfo loadCached() {
        File file = cacheFile();
        if(!file.isFile()) return null;
        try {
            return new Gson().fromJson(Tools.read(file), HostingInfo.class);
        }catch (Exception e) {
            return null;
        }
    }

    private void refreshRemote(View view) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(REMOTE_URL).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                if(connection.getResponseCode() != 200) return;
                String json;
                try(InputStream inputStream = connection.getInputStream()) {
                    json = Tools.read(inputStream);
                }finally {
                    connection.disconnect();
                }
                HostingInfo info = new Gson().fromJson(json, HostingInfo.class);
                if(info == null || info.plans == null) return;
                Tools.write(cacheFile().getAbsolutePath(), json);
                Tools.runOnUiThread(() -> {
                    if(isAdded() && getView() == view) bind(view, info);
                });
            }catch (Exception ignored) {
                // Offline: keep showing the bundled or cached plans
            }
        });
    }

    private void bind(View view, HostingInfo info) {
        Context context = requireContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ((TextView) view.findViewById(R.id.hosting_name)).setText(info.name);
        ((TextView) view.findViewById(R.id.hosting_tagline)).setText(info.tagline);

        View couponView = view.findViewById(R.id.hosting_coupon);
        if(info.coupon != null && !info.coupon.isEmpty()) {
            couponView.setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.hosting_coupon_code)).setText(getString(R.string.hosting_coupon, info.coupon));
            ((TextView) view.findViewById(R.id.hosting_coupon_text)).setText(info.couponText);
            couponView.setOnClickListener(v -> copyCoupon(info.coupon));
        } else {
            couponView.setVisibility(View.GONE);
        }

        Button cta = view.findViewById(R.id.hosting_cta);
        cta.setOnClickListener(v -> Tools.openURL(requireActivity(), info.url));

        LinearLayout features = view.findViewById(R.id.hosting_features);
        features.removeAllViews();
        if(info.features != null) for(Item item : info.features) features.addView(itemView(inflater, features, item));

        LinearLayout plans = view.findViewById(R.id.hosting_plans);
        plans.removeAllViews();
        if(info.plans != null) {
            for(Plan plan : info.plans) {
                View planView = inflater.inflate(R.layout.item_hosting_plan, plans, false);
                ((TextView) planView.findViewById(R.id.plan_name)).setText(plan.name);
                ((TextView) planView.findViewById(R.id.plan_specs)).setText(plan.cpu + " · " + plan.disk);
                ((TextView) planView.findViewById(R.id.plan_price)).setText(plan.price);
                if(plan.highlight) {
                    planView.setBackgroundResource(R.drawable.background_plan_card_highlight);
                    planView.findViewById(R.id.plan_badge).setVisibility(View.VISIBLE);
                }
                String target = plan.url != null ? plan.url : info.url;
                planView.setOnClickListener(v -> Tools.openURL(requireActivity(), target));
                plans.addView(planView);
            }
        }

        LinearLayout extras = view.findViewById(R.id.hosting_extras);
        extras.removeAllViews();
        if(info.extras != null) for(Item item : info.extras) extras.addView(itemView(inflater, extras, item));

        LinearLayout links = view.findViewById(R.id.hosting_links);
        links.removeAllViews();
        if(info.links != null) {
            int margin = (int) (8 * getResources().getDisplayMetrics().density);
            for(Link link : info.links) {
                Button button = (Button) inflater.inflate(R.layout.item_hosting_link, links, false);
                button.setText(link.label);
                button.setOnClickListener(v -> Tools.openURL(requireActivity(), link.url));
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
                params.bottomMargin = margin;
                links.addView(button);
            }
        }
    }

    private static View itemView(LayoutInflater inflater, ViewGroup parent, Item item) {
        View itemView = inflater.inflate(R.layout.item_hosting_feature, parent, false);
        ((TextView) itemView.findViewById(R.id.feature_title)).setText(item.title);
        ((TextView) itemView.findViewById(R.id.feature_text)).setText(item.text);
        return itemView;
    }

    private void copyCoupon(String coupon) {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("cupom", coupon));
        Toast.makeText(requireContext(), getString(R.string.hosting_coupon_copied, coupon), Toast.LENGTH_SHORT).show();
    }
}
