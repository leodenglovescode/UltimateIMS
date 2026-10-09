package io.github.leodenglovescode.ultimateims;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.*;
import com.google.android.material.textview.MaterialTextView;

/** 统一的 Material 3 语义色、间距、字体和触控目标；尺寸使用 dp/sp。 */
final class MaterialUi {
    private MaterialUi() { }
    static int dp(Context c, int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    static int color(Context c, int attr) { return MaterialColors.getColor(c,attr,"UltimateIMS"); }
    static LinearLayout column(Context c) { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static void add(LinearLayout parent, View child) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.bottomMargin = dp(parent.getContext(),12); parent.addView(child,params);
    }
    static TextView text(Context c, String value, boolean heading) {
        MaterialTextView v = new MaterialTextView(c); v.setText(value);
        TextViewCompat.setTextAppearance(v,heading ? com.google.android.material.R.style.TextAppearance_Material3_TitleLarge : com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        v.setTextColor(color(c,com.google.android.material.R.attr.colorOnSurface)); v.setLineSpacing(dp(c,3),1);
        v.setAccessibilityHeading(heading); v.setTextIsSelectable(!heading); v.setPadding(0,dp(c,4),0,dp(c,4)); return v;
    }
    static LinearLayout card(LinearLayout parent, String title) {
        Context c = parent.getContext(); MaterialCardView card = new MaterialCardView(c);
        card.setRadius(dp(c,20)); card.setCardElevation(0); card.setStrokeWidth(0);
        card.setCardBackgroundColor(color(c,com.google.android.material.R.attr.colorSurfaceContainer));
        LinearLayout contents = column(c); contents.setPadding(dp(c,20),dp(c,16),dp(c,20),dp(c,12)); card.addView(contents); add(parent,card);
        if (title != null) add(contents,text(c,title,true)); return contents;
    }
    static MaterialButton button(Context c, int label, Runnable action, boolean primary) {
        int style = primary ? com.google.android.material.R.attr.materialButtonStyle : com.google.android.material.R.attr.materialButtonOutlinedStyle;
        MaterialButton v = new MaterialButton(c,null,style); v.setText(label); v.setAllCaps(false); v.setMinHeight(dp(c,56)); v.setMaxLines(4);
        if (label == R.string.reset) {
            int error = color(c,androidx.appcompat.R.attr.colorError); v.setTextColor(error); v.setStrokeColor(android.content.res.ColorStateList.valueOf(error));
        }
        v.setOnClickListener(w -> action.run()); return v;
    }
    static void note(LinearLayout parent, String value) {
        if (value == null || value.isBlank()) return;
        LinearLayout card = card(parent,null); add(card,text(parent.getContext(),value,false));
    }
    static TextInputLayout input(LinearLayout parent, TextInputEditText field, String label) {
        TextInputLayout layout = new TextInputLayout(parent.getContext()); layout.setHint(label); layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        field.setMinHeight(dp(parent.getContext(),56)); layout.addView(field,new LinearLayout.LayoutParams(-1,-2)); add(parent,layout); return layout;
    }
    static LinearLayout form(Context c) { LinearLayout v = column(c); v.setPadding(dp(c,24),dp(c,8),dp(c,24),dp(c,8)); return v; }
    static ScrollView scrollForm(LinearLayout form) {
        ScrollView scroll = new ScrollView(form.getContext()); scroll.setFillViewport(false); scroll.addView(form); return scroll;
    }
    static View row(Context c, String title, String supporting, Runnable action) {
        LinearLayout row = column(c); row.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14)); row.setMinimumHeight(dp(c,72));
        TextView label = text(c,title,false); label.setTypeface(null,Typeface.BOLD); label.setTextIsSelectable(false); row.addView(label);
        TextView detail = text(c,supporting,false); TextViewCompat.setTextAppearance(detail,com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        detail.setTextColor(color(c,com.google.android.material.R.attr.colorOnSurfaceVariant)); detail.setTextIsSelectable(false); row.addView(detail);
        if (action != null) {
            android.util.TypedValue background = new android.util.TypedValue(); c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,background,true); row.setBackgroundResource(background.resourceId);
            row.setFocusable(true); row.setClickable(true); row.setOnClickListener(v -> action.run());
            row.setContentDescription(title+". "+supporting+". "+c.getString(R.string.edit));
            row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); detail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        return row;
    }
    static final class Choice extends TextInputLayout {
        private final MaterialAutoCompleteTextView field;
        private int selection;
        public Choice(Context c) { this(c,"",java.util.List.of(),0,null); }
        Choice(Context c, String label, java.util.List<String> options, int initial, java.util.function.IntConsumer callback) {
            super(c,null,com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
            setHint(label); setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
            field = new MaterialAutoCompleteTextView(getContext()); field.setInputType(0); field.setSingleLine(false); field.setMaxLines(3); field.setHorizontallyScrolling(false); field.setMinHeight(dp(c,56));
            field.setAdapter(new ArrayAdapter<>(c,R.layout.dropdown_item,options));
            addView(field,new LinearLayout.LayoutParams(-1,-2)); selection = initial;
            if (!options.isEmpty()) field.setText(options.get(initial),false);
            field.setOnItemClickListener((p,v,pos,id) -> { selection = pos; if (callback != null) callback.accept(pos); });
        }
        int selected() { return selection; }
    }
}
