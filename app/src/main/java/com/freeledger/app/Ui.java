package com.freeledger.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static final int BG = Color.rgb(250, 248, 255);
    public static final int CARD = Color.WHITE;
    public static final int TEXT = Color.rgb(51, 44, 59);
    public static final int MUTED = Color.rgb(117, 109, 126);
    public static final int ACCENT = Color.rgb(232, 106, 146);
    public static final int SOFT = Color.rgb(252, 234, 240);
    public static final int LINE = Color.rgb(238, 232, 241);
    public static final int DANGER = Color.rgb(181, 75, 69);

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    public static TextView title(Context c, String s, float sp) {
        TextView t = text(c, s, sp, TEXT);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    public static Button button(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        b.setBackground(roundRect(SOFT, 11, c));
        return b;
    }

    public static Button textButton(Context c, String s, float sp, int color) {
        Button b = new Button(c);
        b.setText(s); b.setTextSize(sp); b.setTextColor(color); b.setAllCaps(false);
        b.setGravity(Gravity.CENTER); b.setPadding(0, 0, 0, 0); b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    public static Button darkButton(Context c, String s) {
        Button b = button(c, s);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundRect(Color.rgb(31,31,29), 11, c));
        return b;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(160,158,151));
        e.setSingleLine(true);
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        e.setBackground(roundStroke(CARD, LINE, 1, 10, c));
        return e;
    }

    public static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    public static GradientDrawable roundRect(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundStroke(int fill, int stroke, int strokeDp, float radiusDp, Context c) {
        GradientDrawable d = roundRect(fill, radiusDp, c);
        d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vertical(c);
        l.setPadding(dp(c, 16), dp(c, 15), dp(c, 16), dp(c, 15));
        l.setBackground(roundStroke(CARD, LINE, 1, 17, c));
        return l;
    }

    public static void applySystemInsets(Activity a, View root, int baseHorizontal, int baseTop, int baseBottom) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(
                    dp(a, baseHorizontal),
                    dp(a, baseTop) + top,
                    dp(a, baseHorizontal),
                    dp(a, baseBottom) + bottom
            );
            return insets;
        });
        root.requestApplyInsets();
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }
}
