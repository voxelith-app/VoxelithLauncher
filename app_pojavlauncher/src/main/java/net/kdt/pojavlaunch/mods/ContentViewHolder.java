package net.kdt.pojavlaunch.mods;

import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ImageReceiver;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;

import java.util.Locale;

import git.artdeell.mojo.R;

/** Row used both by the installed content list and by the Modrinth browser. */
public class ContentViewHolder extends RecyclerView.ViewHolder {
    public final ImageView icon;
    public final TextView title;
    public final TextView subtitle;
    public final ImageButton updateButton;
    public final SwitchCompat enabledSwitch;
    public final ImageButton deleteButton;
    public final Button installButton;
    public final ImageButton versionsButton;
    private final ModIconCache mIconCache;
    @Nullable private ImageReceiver mPendingIcon;
    private static ModIconCache sSharedIconCache;

    /** One cache for all content screens, so opening them again does not spawn more loader threads. */
    public static synchronized ModIconCache sharedIconCache() {
        if(sSharedIconCache == null) sSharedIconCache = new ModIconCache();
        return sSharedIconCache;
    }

    public ContentViewHolder(@NonNull View itemView, ModIconCache iconCache) {
        super(itemView);
        mIconCache = iconCache;
        icon = itemView.findViewById(R.id.content_icon);
        title = itemView.findViewById(R.id.content_title);
        subtitle = itemView.findViewById(R.id.content_subtitle);
        updateButton = itemView.findViewById(R.id.content_update);
        enabledSwitch = itemView.findViewById(R.id.content_switch);
        deleteButton = itemView.findViewById(R.id.content_delete);
        installButton = itemView.findViewById(R.id.content_install);
        versionsButton = itemView.findViewById(R.id.content_versions);
        icon.setClipToOutline(true);
    }

    public void loadIcon(@Nullable String projectId, @Nullable String iconUrl) {
        if(mPendingIcon != null) {
            mIconCache.cancelImage(mPendingIcon);
            mPendingIcon = null;
        }
        icon.setImageDrawable(null);
        if(projectId == null || iconUrl == null || iconUrl.isEmpty()) return;
        ImageReceiver receiver = new ImageReceiver() {
            @Override
            public void onImageAvailable(android.graphics.Bitmap image) {
                if(mPendingIcon != this) return;
                mPendingIcon = null;
                icon.setImageBitmap(image);
            }
        };
        mPendingIcon = receiver;
        mIconCache.getImage(receiver, "vx_" + projectId, iconUrl);
    }

    public static String formatCount(long count) {
        if(count >= 1_000_000) return String.format(Locale.getDefault(), "%.1fM", count / 1_000_000d);
        if(count >= 1_000) return String.format(Locale.getDefault(), "%.1fk", count / 1_000d);
        return Long.toString(count);
    }
}
