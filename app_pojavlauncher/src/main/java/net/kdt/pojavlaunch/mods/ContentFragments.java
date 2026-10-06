package net.kdt.pojavlaunch.mods;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;

import git.artdeell.mojo.R;

/** Helpers shared by the content screens. */
public final class ContentFragments {
    public static final String ARG_TYPE = "content_type";

    private ContentFragments() {}

    public static Bundle args(ContentType type) {
        Bundle bundle = new Bundle();
        bundle.putInt(ARG_TYPE, type.ordinal());
        return bundle;
    }

    public static ContentType typeFrom(@Nullable Bundle bundle) {
        if(bundle == null) return ContentType.MOD;
        int ordinal = bundle.getInt(ARG_TYPE, 0);
        ContentType[] values = ContentType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ContentType.MOD;
    }

    public static String describeTarget(Context context, ContentTarget target) {
        if(target.gameVersion == null) return context.getString(R.string.content_target_unknown);
        if(target.loader == null) return context.getString(R.string.content_target_vanilla, target.gameVersion);
        return context.getString(R.string.content_target, loaderName(target.loader), target.gameVersion);
    }

    private static String loaderName(String loader) {
        switch (loader) {
            case "neoforge": return "NeoForge";
            case "forge": return "Forge";
            case "fabric": return "Fabric";
            case "quilt": return "Quilt";
            default: return loader;
        }
    }

    public interface TabListener {
        void onTabSelected(ContentType type);
    }

    /** Wires the three tabs from view_content_tabs.xml. */
    public static void setupTabs(View tabs, ContentType selected, TabListener listener) {
        Button[] buttons = new Button[]{
                tabs.findViewById(R.id.tab_mods),
                tabs.findViewById(R.id.tab_resourcepacks),
                tabs.findViewById(R.id.tab_shaders)
        };
        ContentType[] types = ContentType.values();
        for(int i = 0; i < buttons.length; i++) {
            final int index = i;
            buttons[i].setSelected(types[i] == selected);
            buttons[i].setOnClickListener(v -> {
                for(int j = 0; j < buttons.length; j++) buttons[j].setSelected(j == index);
                listener.onTabSelected(types[index]);
            });
        }
    }
}
