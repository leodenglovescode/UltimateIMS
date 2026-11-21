package io.github.leodenglovescode.ultimateims;

import android.app.Application;
import android.content.Context;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.concurrent.*;

/** 外观设置与图片导入单独管理，不接触特权接口。 */
public final class AppearanceSettings {
    public static final class ImportState extends AndroidViewModel {
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
        boolean picking, importing, failed;
        int revision;
        public ImportState(Application app) { super(app); }
        void photo(android.net.Uri uri) {
            importing = true;
            worker.execute(() -> {
                boolean success;
                try { AppearanceBackground.importPhoto(getApplication(),uri); success = true; }
                catch (Exception | OutOfMemoryError e) { success = false; android.util.Log.w("UltimateIMS","Background import failed; previous background preserved"); }
                final boolean result = success;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    importing = false; failed = !result; changes.setValue(++revision);
                });
            });
        }
        @Override protected void onCleared() { worker.shutdown(); }
    }
    private final MainActivity activity;
    private final ImportState state;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker;
    AppearanceSettings(MainActivity activity) {
        this.activity = activity;
        state = new ViewModelProvider(activity).get(ImportState.class);
        int seen = state.revision;
        state.changes.observe(activity,value -> {
            if (value == null || value <= seen) return;
            if (state.failed) { Toast.makeText(activity,R.string.background_failed,Toast.LENGTH_LONG).show(); activity.redrawAppearanceSettings(); }
            else activity.recreate();
        });
        picker = activity.registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),uri -> {
            state.picking = false; activity.finishAppearancePicker();
            if (uri != null) state.photo(uri);
            activity.redrawAppearanceSettings();
        });
    }
    boolean busy() { return state.picking || state.importing; }
    void show(LinearLayout parent) {
        Context c = activity;
        String[] styles = {c.getString(R.string.style_material),c.getString(R.string.style_glass)};
        choice(parent,R.string.appearance_style,styles,ThemePreferences.glass(c) ? 1 : 0,index -> { ThemePreferences.set(c,"glass",index == 1); activity.recreate(); });
        String[] modes = {c.getString(R.string.auto),c.getString(R.string.theme_light),c.getString(R.string.theme_dark)};
        choice(parent,R.string.appearance_mode,modes,ThemePreferences.mode(c),index -> ThemePreferences.setMode(c,index));
        MaterialUi.switchRow(parent,c.getString(R.string.dynamic_color),ThemePreferences.dynamic(c),true,value -> { ThemePreferences.setDynamic(c,value); activity.recreate(); });
        MaterialUi.switchRow(parent,c.getString(R.string.solid_surfaces),ThemePreferences.solid(c),true,value -> { ThemePreferences.set(c,"solid",value); activity.recreate(); });
        MaterialUi.add(parent,MaterialUi.row(c,c.getString(R.string.custom_background),c.getString(state.importing ? R.string.background_importing : AppearanceBackground.exists(c) ? R.string.background_selected : R.string.background_none),busy() ? null : () -> {
            state.picking = true;
            try { picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()); }
            catch (RuntimeException e) { state.picking = false; Toast.makeText(c,R.string.background_failed,Toast.LENGTH_LONG).show(); }
        }));
        if (AppearanceBackground.exists(c)) {
            var remove = MaterialUi.button(c,R.string.background_remove,() -> { AppearanceBackground.remove(c); activity.recreate(); },false);
            remove.setEnabled(!busy()); MaterialUi.add(parent,remove);
        }
        if (!ThemePreferences.solid(c)) {
            slider(parent,R.string.background_visibility,"background_visibility",ThemePreferences.backgroundVisibility(c),0,100,true);
            if (ThemePreferences.glass(c)) {
                slider(parent,R.string.glass_opacity,"glass_opacity",ThemePreferences.glassOpacity(c),10,90,true);
                slider(parent,R.string.glass_blur,"glass_blur",ThemePreferences.glassBlur(c),0,40,false);
            }
        }
        MaterialUi.add(parent,MaterialUi.text(c,c.getString(R.string.background_help),false));
        MaterialUi.switchRow(parent,c.getString(R.string.compact_rows),ThemePreferences.compact(c),true,value -> { ThemePreferences.set(c,"compact",value); activity.recreate(); });
        MaterialUi.switchRow(parent,c.getString(R.string.keep_awake),ThemePreferences.keepAwake(c),true,value -> { ThemePreferences.set(c,"keep_awake",value); activity.applyScreenPreference(); });
        var reset = MaterialUi.button(c,R.string.appearance_reset,() -> new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.appearance_reset).setMessage(R.string.appearance_reset_help).setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.confirm,(dialog,which) -> {
                    AppearanceBackground.remove(c); ThemePreferences.reset(c); activity.recreate();
                }).show(),false);
        reset.setEnabled(!busy()); MaterialUi.add(parent,reset);
    }
    private void slider(LinearLayout parent, int title, String key, int initial, int min, int max, boolean percentage) {
        TextView label = MaterialUi.text(activity,activity.getString(percentage ? R.string.appearance_percent : R.string.appearance_blur_value,activity.getString(title),initial),false);
        MaterialUi.add(parent,label);
        var slider = new com.google.android.material.slider.Slider(activity);
        slider.setValueFrom(min); slider.setValueTo(max); slider.setStepSize(1); slider.setValue(initial);
        slider.setContentDescription(activity.getString(title));
        slider.setLabelFormatter(value -> activity.getString(percentage ? R.string.appearance_percent_value : R.string.appearance_dp_value,Math.round(value)));
        slider.addOnChangeListener((view,value,fromUser) -> {
            if (!fromUser) return;
            int selected = Math.round(value); ThemePreferences.setInteger(activity,key,selected);
            label.setText(activity.getString(percentage ? R.string.appearance_percent : R.string.appearance_blur_value,activity.getString(title),selected));
            activity.updateAppearancePreview();
        });
        if (ThemePreferences.glassActive(activity)) {
            slider.setTrackActiveTintList(android.content.res.ColorStateList.valueOf(ThemePreferences.dark(activity) ? 0xffa0e7f5 : 0xff16587d));
            slider.setTrackInactiveTintList(android.content.res.ColorStateList.valueOf(0x50ffffff));
            slider.setThumbTintList(android.content.res.ColorStateList.valueOf(0xfff8fcff));
        }
        MaterialUi.add(parent,slider);
    }
    private void choice(LinearLayout parent, int title, String[] values, int selected, java.util.function.IntConsumer change) {
        MaterialUi.add(parent,MaterialUi.row(activity,activity.getString(title),values[selected],() -> new MaterialAlertDialogBuilder(activity)
                .setTitle(title).setSingleChoiceItems(values,selected,(dialog,index) -> { dialog.dismiss(); if (index != selected) change.accept(index); })
                .setNegativeButton(R.string.cancel,null).show()));
    }
}
