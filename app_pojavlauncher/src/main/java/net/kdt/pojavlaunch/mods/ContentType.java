package net.kdt.pojavlaunch.mods;

import androidx.annotation.StringRes;

import git.artdeell.mojo.R;

public enum ContentType {
    MOD("mod", "mods", R.string.content_type_mods),
    RESOURCE_PACK("resourcepack", "resourcepacks", R.string.content_type_resourcepacks),
    SHADER("shader", "shaderpacks", R.string.content_type_shaders);

    public final String modrinthType;
    public final String folderName;
    @StringRes public final int title;

    ContentType(String modrinthType, String folderName, @StringRes int title) {
        this.modrinthType = modrinthType;
        this.folderName = folderName;
        this.title = title;
    }

    public boolean isContentFile(String fileName) {
        String name = fileName.toLowerCase();
        if(this == MOD) return name.endsWith(".jar") || name.endsWith(".jar.disabled");
        return name.endsWith(".zip") || name.endsWith(".zip.disabled");
    }
}
