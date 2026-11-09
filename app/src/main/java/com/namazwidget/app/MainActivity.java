package com.namazwidget.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int green = Color.rgb(20,43,39);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32,45,32,32);
        root.setBackgroundColor(green);
        TextView title = new TextView(this);
        title.setText("☪  NamazWidget");
        title.setTextColor(Color.rgb(231,201,130));
        title.setTextSize(27);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("\nВиджеты 4×1, 2×2, 4×2 и 4×3\nУдерживай свободное место на рабочем столе → Виджеты → NamazWidget.\n");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(16);
        root.addView(subtitle);

        TextView value = new TextView(this);
        value.setTextColor(Color.WHITE);
        value.setTextSize(17);
        root.addView(value);
        int start = getPreferences(MODE_PRIVATE).getInt("opacity", 73);
        value.setText("Прозрачность фона: " + (100-start) + "%");
        SeekBar seek = new SeekBar(this);
        seek.setMax(90);
        seek.setProgress(start-10);
        root.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s,int progress,boolean user) {
                int opacity = progress+10;
                value.setText("Прозрачность фона: " + (100-opacity) + "%");
                if (user) { getPreferences(MODE_PRIVATE).edit().putInt("opacity",opacity).apply(); refresh(); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        CheckBox haze = new CheckBox(this);
        haze.setText("Матовое стекло (мягкий размытый рисунок)");
        haze.setTextColor(Color.WHITE);
        haze.setChecked(getPreferences(MODE_PRIVATE).getBoolean("haze",true));
        haze.setOnCheckedChangeListener((button,checked)->{
            getPreferences(MODE_PRIVATE).edit().putBoolean("haze",checked).apply(); refresh();
        });
        root.addView(haze);
        TextView note = new TextView(this);
        note.setText("\nНастоящее размытие обоев зависит от лаунчера Android. Матовое стекло, цвет и прозрачность работают без специальных разрешений.\n\nВремя намаза берётся только из бумажного календаря. Неуказанные даты пока показывают прочерки, а не выдуманные значения.");
        note.setTextColor(Color.rgb(184,211,201));
        note.setTextSize(14);
        root.addView(note);
        setContentView(root);
    }
    private void refresh() {
        AppWidgetManager wm=AppWidgetManager.getInstance(this);
        Class<?>[] providers={PrayerWidget.Strip.class,PrayerWidget.Small.class,PrayerWidget.Medium.class,PrayerWidget.Large.class};
        for(Class<?> p:providers) {
            int[] ids=wm.getAppWidgetIds(new ComponentName(this,p));
            PrayerWidget.update(this,wm,ids,p.getSimpleName());
        }
    }
}
