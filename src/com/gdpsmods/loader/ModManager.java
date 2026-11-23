package com.gdpsmods.loader;

import android.app.Activity;
import android.content.SharedPreferences;
import dalvik.system.DexClassLoader;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class ModManager {
    public static final StringBuilder LOG = new StringBuilder();
    public static final List<ModInfo> MODS = new ArrayList<ModInfo>();
    static Activity act;
    static SharedPreferences prefs;

    public static void init(Activity a, SharedPreferences p) { act = a; prefs = p; }

    public static void log(String s) {
        LOG.append(s).append('\n');
        android.util.Log.i("ModLoader", s);
    }

    public static File modsDir() {
        File d = new File(act.getExternalFilesDir(null), "mods");
        d.mkdirs();
        return d;
    }

    static ModInfo find(String id) {
        for (ModInfo m : MODS) if (m.id.equals(id)) return m;
        return null;
    }

    public static void setEnabled(ModInfo m, boolean on) {
        m.enabled = on;
        prefs.edit().putBoolean("en_" + m.id, on).commit();
    }

    public static void scan() {
        Iterator<ModInfo> it = MODS.iterator();
        while (it.hasNext()) if (!it.next().file.exists()) it.remove();
        File[] files = modsDir().listFiles();
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            String n = f.getName();
            String type = n.endsWith(".so") ? "so" : (n.endsWith(".dex") || n.endsWith(".jar")) ? "dex" : null;
            if (type == null) continue;
            String id = n.substring(0, n.lastIndexOf('.'));
            ModInfo m = find(id);
            if (m == null) {
                m = new ModInfo();
                m.id = id;
                m.name = id;
                m.enabled = prefs.getBoolean("en_" + id, true);
                MODS.add(m);
            }
            m.file = f;
            m.type = type;
            File meta = new File(modsDir(), id + ".json");
            if (meta.exists()) {
                try {
                    JSONObject j = new JSONObject(readAll(meta));
                    m.name = j.optString("name", id);
                    m.version = j.optString("version", "?");
                    m.author = j.optString("author", "?");
                    m.description = j.optString("description", "");
                    m.main = j.optString("main", null);
                } catch (Exception e) {
                    log("JSON invalido: " + id);
                }
            }
        }
    }

    public static void load(ModInfo m) {
        if (m.loaded) return;
        prefs.edit().putString("current", m.id).commit(); // para detectar al culpable si crashea
        try {
            File priv = new File(act.getCodeCacheDir(), "mods");
            priv.mkdirs();
            File copy = new File(priv, m.file.getName());
            if (copy.exists()) { copy.setWritable(true); copy.delete(); }
            copy(m.file, copy);
            copy.setReadOnly(); // Android 14 exige dex de solo lectura
            if (m.type.equals("so")) {
                System.load(copy.getAbsolutePath());
            } else {
                if (m.main == null) throw new Exception("falta \"main\" en " + m.id + ".json");
                File odex = new File(priv, "odex");
                odex.mkdirs();
                DexClassLoader cl = new DexClassLoader(copy.getAbsolutePath(), odex.getAbsolutePath(), null, act.getClassLoader());
                Object o = cl.loadClass(m.main).newInstance();
                ((ModEntry) o).onLoad(act);
            }
            m.loaded = true;
            m.error = null;
            log("OK: " + m.id);
        } catch (Throwable t) {
            m.error = t.toString();
            log("ERROR " + m.id + ": " + t);
        }
        prefs.edit().remove("current").commit();
    }

    public static void install(JSONObject o) throws Exception {
        String id = o.getString("id");
        String ext = o.optString("type", "dex").equals("so") ? ".so" : ".dex";
        download(o.getString("url"), new File(modsDir(), id + ext));
        FileWriter w = new FileWriter(new File(modsDir(), id + ".json"));
        w.write(o.toString());
        w.close();
        scan();
        log("Instalado desde tienda: " + id);
    }

    public static String http(String u) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        pipe(open(u), o);
        return o.toString("UTF-8");
    }

    public static void download(String u, File f) throws Exception {
        if (f.exists()) { f.setWritable(true); f.delete(); }
        pipe(open(u), new FileOutputStream(f));
    }

    static InputStream open(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "GDPSModLoader");
        return c.getInputStream();
    }

    static void pipe(InputStream in, OutputStream out) throws Exception {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        in.close();
        out.close();
    }

    static void copy(File a, File b) throws Exception { pipe(new FileInputStream(a), new FileOutputStream(b)); }

    static String readAll(File f) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        pipe(new FileInputStream(f), o);
        return o.toString("UTF-8");
    }
    }
