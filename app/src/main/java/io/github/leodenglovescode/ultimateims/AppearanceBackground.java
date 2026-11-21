package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.AtomicFile;
import android.view.View;
import android.widget.*;
import java.io.*;

/** 私有背景仅保留像素，不保存来源 URI、文件名或 EXIF，且不参与备份和诊断导出。 */
final class AppearanceBackground {
    private AppearanceBackground() { }
    static AtomicFile file(Context c) { return new AtomicFile(new File(c.getNoBackupFilesDir(),"appearance-background.png")); }
    static boolean exists(Context c) { return file(c).getBaseFile().isFile(); }
    static void remove(Context c) { file(c).delete(); }
    static void importPhoto(Context c, Uri uri) throws IOException {
        // 在解码前限制输出尺寸；拒绝损坏或不完整的图片，旧背景保留到原子写入完成。
        Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(),uri),(decoder,info,source) -> {
            int width = info.getSize().getWidth(), height = info.getSize().getHeight();
            float scale = Math.min(1f,1600f / Math.max(width,height));
            decoder.setTargetSize(Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setOnPartialImageListener(error -> false);
        });
        AtomicFile target = file(c); FileOutputStream stream = null;
        try {
            stream = target.startWrite();
            if (!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)) throw new IOException("Background encoding failed");
            target.finishWrite(stream);
        } catch (IOException | RuntimeException | OutOfMemoryError e) { if (stream != null) target.failWrite(stream); throw e; }
        finally { bitmap.recycle(); }
    }
    static Scene wrap(Context c, View foreground) {
        Scene scene = new Scene(c); scene.addView(foreground,new FrameLayout.LayoutParams(-1,-1)); return scene;
    }

    /** 清晰壁纸与共享的模糊渲染节点分开，只有玻璃组件采样模糊层。 */
    static final class Scene extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.drawable.Drawable wallpaper;
        private final RenderNode frost = new RenderNode("UltimateIMS glass backdrop");
        private final java.util.Set<GlassSurface> surfaces = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
        private final android.view.ViewTreeObserver.OnScrollChangedListener scrolling = this::invalidateGlass;
        private final int surface;
        private final boolean decorated;
        private int visibility, opacity, blur;
        private boolean dirty = true;
        private Shader edgeShade;
        private android.view.ViewTreeObserver observer;
        Scene(Context c) {
            super(c); setWillNotDraw(false); setClipChildren(false);
            surface = MaterialUi.color(c,com.google.android.material.R.attr.colorSurface);
            decorated = !ThemePreferences.solid(c) && (exists(c) || ThemePreferences.glass(c));
            Bitmap bitmap = null;
            if (decorated && exists(c)) {
                try (InputStream input = file(c).openRead()) { bitmap = BitmapFactory.decodeStream(input); }
                catch (IOException e) { android.util.Log.w("UltimateIMS","Private background unavailable"); }
            }
            if (bitmap != null) {
                android.graphics.drawable.BitmapDrawable photo = new android.graphics.drawable.BitmapDrawable(c.getResources(),bitmap);
                photo.setFilterBitmap(true); wallpaper = photo;
            } else {
                int[] palette = ThemePreferences.dark(c) ? new int[]{0xff183b69,0xff65497b,0xff237d92}
                        : new int[]{0xff8bc5ff,0xffe4b8ec,0xff8bdad5};
                wallpaper = new GradientDrawable(GradientDrawable.Orientation.TL_BR,palette);
            }
            refresh();
        }
        void refresh() {
            visibility = ThemePreferences.backgroundVisibility(getContext());
            opacity = ThemePreferences.glassOpacity(getContext());
            blur = ThemePreferences.glassBlur(getContext());
            dirty = true; invalidate(); invalidateGlass();
        }
        int opacity() { return opacity; }
        void register(GlassSurface view) { surfaces.add(view); }
        void unregister(GlassSurface view) { surfaces.remove(view); }
        private void invalidateGlass() { for (GlassSurface view : surfaces) view.invalidate(); }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow(); observer = getViewTreeObserver(); observer.addOnScrollChangedListener(scrolling);
        }
        @Override protected void onDetachedFromWindow() {
            if (observer != null && observer.isAlive()) observer.removeOnScrollChangedListener(scrolling);
            frost.discardDisplayList(); dirty = true; super.onDetachedFromWindow();
        }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w,h,oldw,oldh);
            int shade = ThemePreferences.dark(getContext()) ? 0x66000000 : 0x66ffffff;
            edgeShade = new LinearGradient(0,0,0,Math.max(1,h),new int[]{shade,0x00000000,0x00000000,shade},new float[]{0,.12f,.85f,1},Shader.TileMode.CLAMP);
            dirty = true; invalidateGlass();
        }
        private void backdrop(Canvas canvas) {
            int w = getWidth(), h = getHeight(); canvas.drawColor(surface);
            if (!decorated || w == 0 || h == 0) return;
            int iw = wallpaper.getIntrinsicWidth(), ih = wallpaper.getIntrinsicHeight();
            if (iw > 0 && ih > 0) {
                float scale = Math.max(w/(float)iw,h/(float)ih);
                int dw = Math.round(iw*scale), dh = Math.round(ih*scale);
                wallpaper.setBounds((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2);
            } else wallpaper.setBounds(0,0,w,h);
            wallpaper.setAlpha(Math.round(visibility*2.55f)); wallpaper.draw(canvas);
            // 顶底渐变只保护系统栏，保留中间壁纸细节。
            paint.setShader(edgeShade);
            canvas.drawRect(0,0,w,h,paint); paint.setShader(null);
        }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); backdrop(canvas); }
        void drawFrost(Canvas canvas, int x, int y) {
            canvas.save(); canvas.translate(-x,-y);
            if (canvas.isHardwareAccelerated()) {
                if (dirty || !frost.hasDisplayList()) {
                    frost.setPosition(0,0,getWidth(),getHeight());
                    RecordingCanvas recording = frost.beginRecording(getWidth(),getHeight());
                    backdrop(recording); frost.endRecording();
                    float radius = MaterialUi.dp(getContext(),blur);
                    frost.setRenderEffect(radius > 0 ? RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP) : null);
                    dirty = false;
                }
                canvas.drawRenderNode(frost);
            } else backdrop(canvas);
            canvas.restore();
        }
    }
}
