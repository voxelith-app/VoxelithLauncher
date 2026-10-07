package net.kdt.pojavlaunch.mods;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.lang.ref.WeakReference;

/** Loads gallery screenshots downsampled to the view width, so big images don't eat RAM on weak phones. */
public final class GalleryImageLoader {
    private GalleryImageLoader() {}

    public static void load(ImageView target, String url, int targetWidth) {
        WeakReference<ImageView> viewRef = new WeakReference<>(target);
        target.setTag(url);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File cacheDir = new File(Tools.DIR_CACHE, "mod_gallery");
                FileUtils.ensureDirectorySilently(cacheDir);
                File cacheFile = new File(cacheDir, Integer.toHexString(url.hashCode()) + ".img");
                if(!cacheFile.isFile()) DownloadUtils.downloadFile(url, cacheFile);

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while(bounds.outWidth / (options.inSampleSize * 2) >= targetWidth) options.inSampleSize *= 2;
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), options);
                if(bitmap == null) {
                    boolean ignored = cacheFile.delete();
                    return;
                }
                Tools.runOnUiThread(() -> {
                    ImageView view = viewRef.get();
                    if(view != null && url.equals(view.getTag())) view.setImageBitmap(bitmap);
                });
            }catch (Exception ignored) {
                // A missing screenshot is not worth an error dialog
            }
        });
    }
}
