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

    public static String versionLabel(ModrinthModels.Version version) {
        StringBuilder label = new StringBuilder(version.versionNumber != null ? version.versionNumber : version.name);
        if(version.versionType != null && !"release".equals(version.versionType)) label.append(" (").append(version.versionType).append(')');
        if(version.loaders != null && version.loaders.length > 0) label.append(" · ").append(android.text.TextUtils.join(", ", version.loaders));
        return label.toString();
    }

    /** Turns a Modrinth markdown description into plain readable text, without pulling a markdown library. */
    public static CharSequence markdownToText(@Nullable String markdown) {
        if(markdown == null) return "";
        String text = markdown
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "")
                .replaceAll("(?i)<img[^>]*>", "")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\*\\*|__|`", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "• ");
        String html = text.replace("\n", "<br>");
        String plain = androidx.core.text.HtmlCompat.fromHtml(html, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString();
        return plain.replaceAll("\n{3,}", "\n\n").trim();
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
