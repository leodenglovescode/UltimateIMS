package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.LinearLayout;

/** 背景采样、模糊、透射色和高光边缘分层绘制；子视图保持清晰并保留原生可访问性。 */
final class GlassSurface extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path clip = new Path();
    private final int[] here = new int[2], origin = new int[2];
    private final float radius;
    private final boolean dark;
    private Shader sheen, edge;
    private AppearanceBackground.Scene scene;
    public GlassSurface(Context c) { this(c,28); }
    GlassSurface(Context c, int cornerDp) {
        super(c); setOrientation(VERTICAL); setWillNotDraw(false); setClipChildren(false);
        radius = MaterialUi.dp(c,cornerDp); dark = ThemePreferences.dark(c);
        setElevation(MaterialUi.dp(c,4));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),radius); outline.setAlpha(.25f); }
        });
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (ViewParent parent = getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof AppearanceBackground.Scene found) { scene = found; scene.register(this); break; }
        }
    }
    @Override protected void onDetachedFromWindow() { if (scene != null) scene.unregister(this); scene = null; super.onDetachedFromWindow(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        sheen = new LinearGradient(0,0,Math.max(1,w*.7f),Math.max(1,Math.min(h,MaterialUi.dp(getContext(),200))),
                new int[]{dark ? 0x22ffffff : 0x44ffffff,0x03ffffff,0x08ffffff},null,Shader.TileMode.CLAMP);
        edge = new LinearGradient(0,0,Math.max(1,w),Math.max(1,Math.min(h,MaterialUi.dp(getContext(),280))),
                new int[]{0xaaffffff,0x25ffffff,0x0cffffff,0x66ffffff},null,Shader.TileMode.CLAMP);
        bounds.set(.5f,.5f,w-.5f,h-.5f); clip.reset(); clip.addRoundRect(bounds,radius,radius,Path.Direction.CW); invalidateOutline();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        canvas.save(); canvas.clipPath(clip);
        if (scene != null) {
            getLocationInWindow(here); scene.getLocationInWindow(origin);
            scene.drawFrost(canvas,here[0]-origin[0],here[1]-origin[1]);
        }
        int alpha = Math.round((scene == null ? ThemePreferences.glassOpacity(getContext()) : scene.opacity())*2.55f);
        int tint = dark ? 0x00101723 : 0x00ffffff;
        paint.setStyle(Paint.Style.FILL); paint.setColor((alpha<<24)|tint); canvas.drawRoundRect(bounds,radius,radius,paint);
        // 大面积轻薄反光与一像素亮边形成玻璃层次，不叠加不透明 Material 表面。
        paint.setColor(Color.WHITE);
        paint.setShader(sheen);
        canvas.drawRoundRect(bounds,radius,radius,paint); paint.setShader(null);
        canvas.restore();
        paint.setShader(edge);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1,MaterialUi.dp(getContext(),1)));
        canvas.drawRoundRect(bounds,radius,radius,paint); paint.setStyle(Paint.Style.FILL); paint.setShader(null);
    }
}
