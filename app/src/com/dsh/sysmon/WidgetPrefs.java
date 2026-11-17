package com.dsh.sysmon;

import android.content.Context;
import android.content.SharedPreferences;

public final class WidgetPrefs {
    private static final String NAME = "widget";
    private static final String KEY = "preset";
    private static final String KEY_SCRIM = "scrim";
    public static final int DEFAULT = 0;

    private WidgetPrefs() {}

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static int preset(Context c) {
        return sp(c).getInt(KEY, DEFAULT);
    }

    public static void setPreset(Context c, int i) {
        sp(c).edit().putInt(KEY, i).apply();
    }

    /** 遮罩强度：0 无 / 1 轻 / 2 中 / 3 强 */
    public static int scrim(Context c) {
        return sp(c).getInt(KEY_SCRIM, 1);
    }

    public static void setScrim(Context c, int level) {
        sp(c).edit().putInt(KEY_SCRIM, level).apply();
    }
}
