package com.gdpsmods.loader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModsUI {
    static int dp(Activity a, int v) { return (int) (v * a.getResources().getDisplayMetrics().density); }

    static void toast(Activity a, String s) { Toast.makeText(a, s, Toast.LENGTH_SHORT).show(); }

    public static void install(final Activity a) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                Button b = new Button(a);
                b.setText("Mods");
                b.setAllCaps(false);
                b.setTextSize(12);
                b.setTextColor(0xFFFFFFFF);
                b.setBackgroundColor(0xAA000000);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(a, 64), dp(a, 36));
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.topMargin = dp(a, 8);
                lp.rightMargin = dp(a, 8);

                final float[] d = new float[5];
                b.setOnTouchListener(new View.OnTouchListener() {
                    public boolean onTouch(View v, MotionEvent e) {
                        switch (e.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                d[0] = e.getRawX() - v.getTranslationX();
                                d[1] = e.getRawY() - v.getTranslationY();
                                d[2] = e.getRawX();
                                d[3] = e.getRawY();
                                d[4] = 0;
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                if (Math.abs(e.getRawX() - d[2]) + Math.abs(e.getRawY() - d[3]) > 25) d[4] = 1;
                                v.setTranslationX(e.getRawX() - d[0]);
                                v.setTranslationY(e.getRawY() - d[1]);
                                return true;
                            case MotionEvent.ACTION_UP:
                                if (d[4] == 0) showMods(a);
                                return true;
                        }
                        return false;
                    }
                });
                a.addContentView(b, lp);
            }
        }, 1500);
    }

    static void showMods(final Activity a) {
        ModManager.scan();
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));

        if (ModManager.MODS.isEmpty()) {
            TextView t = new TextView(a);
            t.setText("No hay mods instalados.\nCarpeta:\n" + ModManager.modsDir());
            col.addView(t);
        }
        for (final ModInfo m : ModManager.MODS) {
            CheckBox cb = new CheckBox(a);
            String txt = m.name + "  v" + m.version + "\n" + m.author
                + (m.description.isEmpty() ? "" : " - " + m.description)
                + (m.error != null ? "\n[ERROR] " + m.error : "");
            cb.setText(txt);
            cb.setChecked(m.enabled);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean on) {
                    ModManager.setEnabled(m, on);
                    if (on && !m.loaded) ModManager.load(m);
                    toast(a, on ? (m.loaded ? "Mod activado" : "Fallo: mira el Registro")
                                : "Se desactiva al reiniciar el juego");
                }
            });
            col.addView(cb);
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(col);

        new AlertDialog.Builder(a)
            .setTitle("Mods (" + ModManager.MODS.size() + ")")
            .setView(sv)
            .setPositiveButton("Cerrar", null)
            .setNegativeButton("Tienda", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { showStore(a); }
            })
            .setNeutralButton("Registro", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { showLog(a); }
            })
            .show();
    }

    static void showLog(Activity a) {
        TextView t = new TextView(a);
        t.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
        t.setTextSize(11);
        t.setText(ModManager.LOG.length() == 0 ? "(vacio)" : ModManager.LOG.toString());
        ScrollView sv = new ScrollView(a);
        sv.addView(t);
        new AlertDialog.Builder(a).setTitle("Registro").setView(sv).setPositiveButton("OK", null).show();
    }

    static void showStore(final Activity a) {
        toast(a, "Cargando tienda...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    String url = "https://raw.githubusercontent.com/" + ModLoader.REPO + "/main/mods/index.json";
                    final JSONArray arr = new JSONObject(ModManager.http(url)).getJSONArray("mods");
                    a.runOnUiThread(new Runnable() {
                        public void run() { buildStore(a, arr); }
                    });
                } catch (final Exception e) {
                    a.runOnUiThread(new Runnable() {
                        public void run() { toast(a, "No se pudo abrir la tienda: " + e.getMessage()); }
                    });
                }
            }
        }).start();
    }

    static void buildStore(final Activity a, JSONArray arr) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
        try {
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject o = arr.getJSONObject(i);
                TextView t = new TextView(a);
                t.setText(o.optString("name") + "  v" + o.optString("version") + "\n"
                    + o.optString("author") + " - " + o.optString("description"));
                final Button b = new Button(a);
                b.setText("Descargar / Actualizar");
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        b.setEnabled(false);
                        new Thread(new Runnable() {
                            public void run() {
                                String msg;
                                try {
                                    ModManager.install(o);
                                    msg = "Instalado. Reinicia el juego para aplicarlo.";
                                } catch (Exception e) { msg = "Error: " + e.getMessage(); }
                                final String m2 = msg;
                                a.runOnUiThread(new Runnable() {
                                    public void run() { toast(a, m2); b.setEnabled(true); }
                                });
                            }
                        }).start();
                    }
                });
                col.addView(t);
                col.addView(b);
            }
        } catch (Exception e) { toast(a, "index.json invalido"); }
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        new AlertDialog.Builder(a).setTitle("Tienda de mods").setView(sv).setPositiveButton("Cerrar", null).show();
    }
                        }
