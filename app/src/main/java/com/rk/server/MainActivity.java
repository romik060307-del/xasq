package com.rk.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.Deflater;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends Activity {
    private static final String LOCAL_ADMIN_PREFS = "ohli_local_admin";
    private static final String ADMIN_PIN_HASH = "admin_pin_hash";
    private static final String PUBLIC_URL_KEY = "public_tunnel_url";
    private static final int BG = Color.rgb(244, 247, 246);
    private static final int INK = Color.rgb(24, 43, 42);
    private static final int MUTED = Color.rgb(99, 116, 113);
    private static final int GREEN = Color.rgb(36, 122, 102);
    private static final int WHITE = Color.WHITE;

    private TextView statusText;
    private TextView addressText;
    private TextView detailText;
    private Button startButton;
    private Button stopButton;
    private Button inboxButton;
    private long inboxFileModified = Long.MIN_VALUE;
    private final Handler handler = new Handler();
    private boolean foreground;

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            refreshStatus();
            refreshInboxBadge();
            if (foreground) handler.postDelayed(this, 3000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (android.os.Build.VERSION.SDK_INT >= 21) getWindow().setStatusBarColor(Color.rgb(16, 45, 43));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(buildLayout());
        refreshStatus();
        refreshInboxBadge();
    }

    private View buildLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        scroll.addView(root);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("OHLI  /  SOCIAL SERVER");
        eyebrow.setTextColor(GREEN);
        eyebrow.setTextSize(12);
        if (android.os.Build.VERSION.SDK_INT >= 21) eyebrow.setLetterSpacing(0.08f);
        root.addView(eyebrow, matchWrap());

        TextView title = new TextView(this);
        title.setText("OHLI Social Server");
        title.setTextColor(INK);
        title.setTextSize(30);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(7);
        root.addView(title, titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Host OHLI Social untuk chat, feed, Story, media, LIVE, dan verifikasi OTP. Permintaan login/daftar akan muncul sebagai notifikasi pribadi berisi nomor dan kode OTP; teruskan kode secara manual melalui WhatsApp. Akses dari luar jaringan wajib memakai HTTPS tunnel tepercaya.");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(15);
        subtitle.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(8);
        root.addView(subtitle, subParams);

        inboxButton = makeButton("🔔 PUSAT NOTIFIKASI (0)", Color.rgb(229, 239, 235), INK);
        LinearLayout.LayoutParams inboxParams = matchWrap();
        inboxParams.topMargin = dp(16);
        root.addView(inboxButton, inboxParams);
        inboxButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showServerInbox(); }
        });

        Button usersButton = makeButton("👥 PANEL PENGGUNA & MODERASI", Color.rgb(231, 237, 255), Color.rgb(40, 57, 110));
        LinearLayout.LayoutParams usersParams = matchWrap(); usersParams.topMargin = dp(9); root.addView(usersButton, usersParams);
        usersButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { requestLocalAdminPin(); } });

        Button tunnelButton = makeButton("☁ TUNNEL & LINK PUBLIK", Color.rgb(232, 238, 236), INK);
        LinearLayout.LayoutParams tunnelParams = matchWrap(); tunnelParams.topMargin = dp(9); root.addView(tunnelButton, tunnelParams);
        tunnelButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showTunnelSettings(); } });

        Button keepAliveButton = makeButton("⚡ PENGATURAN SERVER 24 JAM", Color.rgb(255, 242, 222), Color.rgb(130, 76, 25));
        LinearLayout.LayoutParams keepAliveParams = matchWrap(); keepAliveParams.topMargin = dp(9); root.addView(keepAliveButton, keepAliveParams);
        keepAliveButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showKeepAliveGuide(); } });

        Button backupButton = makeButton("🛡 BUAT BACKUP DATA SEKARANG", Color.rgb(231, 246, 237), Color.rgb(30, 100, 65));
        LinearLayout.LayoutParams backupParams = matchWrap(); backupParams.topMargin = dp(9); root.addView(backupButton, backupParams);
        backupButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { createDataBackup(); } });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackgroundColor(WHITE);
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(22);
        root.addView(card, cardParams);

        statusText = new TextView(this);
        statusText.setText("SERVER MATI");
        statusText.setTextColor(MUTED);
        statusText.setTextSize(12);
        statusText.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(statusText, matchWrap());

        addressText = new TextView(this);
        addressText.setText("Belum ada alamat server");
        addressText.setTextColor(INK);
        addressText.setTextSize(18);
        addressText.setTypeface(null, android.graphics.Typeface.BOLD);
        addressText.setTextIsSelectable(true);
        LinearLayout.LayoutParams addressParams = matchWrap();
        addressParams.topMargin = dp(9);
        card.addView(addressText, addressParams);

        detailText = new TextView(this);
        detailText.setText("Port 8080 · web dan data chat berjalan dari tablet ini");
        detailText.setTextColor(MUTED);
        detailText.setTextSize(12);
        LinearLayout.LayoutParams detailParams = matchWrap();
        detailParams.topMargin = dp(8);
        card.addView(detailText, detailParams);

        startButton = makeButton("MULAI SERVER", GREEN, WHITE);
        LinearLayout.LayoutParams startParams = matchWrap();
        startParams.topMargin = dp(18);
        card.addView(startButton, startParams);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ServerService.class);
                intent.setAction(ServerService.ACTION_START);
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
                Toast.makeText(MainActivity.this, "Server sedang dinyalakan…", Toast.LENGTH_SHORT).show();
                handler.postDelayed(new Runnable() { @Override public void run() { refreshStatus(); } }, 800L);
            }
        });

        stopButton = makeButton("MATIKAN SERVER", Color.rgb(232, 238, 236), INK);
        LinearLayout.LayoutParams stopParams = matchWrap();
        stopParams.topMargin = dp(9);
        card.addView(stopButton, stopParams);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ServerService.class);
                intent.setAction(ServerService.ACTION_STOP);
                if (android.os.Build.VERSION.SDK_INT >= 26 && ServerService.isRunning) startForegroundService(intent); else startService(intent);
                refreshStatus();
                Toast.makeText(MainActivity.this, "Permintaan mematikan server dikirim", Toast.LENGTH_SHORT).show();
            }
        });

        Button openButton = makeButton("BUKA HALAMAN CHAT", Color.rgb(232, 238, 236), INK);
        LinearLayout.LayoutParams openParams = matchWrap();
        openParams.topMargin = dp(9);
        card.addView(openButton, openParams);
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String url = getServerUrl();
                if (url == null) {
                    Toast.makeText(MainActivity.this, "Nyalakan server dan sambungkan tablet ke Wi-Fi terlebih dahulu", Toast.LENGTH_LONG).show();
                    return;
                }
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "Browser tidak ditemukan", Toast.LENGTH_LONG).show(); }
            }
        });

        Button copyButton = makeButton("SALIN ALAMAT SERVER", Color.rgb(232, 238, 236), INK);
        LinearLayout.LayoutParams copyParams = matchWrap();
        copyParams.topMargin = dp(9);
        card.addView(copyButton, copyParams);
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String url = getServerUrl();
                if (url == null) {
                    Toast.makeText(MainActivity.this, "Belum ada alamat IP. Pastikan Wi-Fi tersambung", Toast.LENGTH_LONG).show();
                    return;
                }
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Alamat RKM Server", url));
                Toast.makeText(MainActivity.this, "Alamat disalin: " + url, Toast.LENGTH_LONG).show();
            }
        });

        TextView stepsTitle = new TextView(this);
        stepsTitle.setText("CARA MENGUJI");
        stepsTitle.setTextColor(INK);
        stepsTitle.setTextSize(12);
        stepsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams stp = matchWrap();
        stp.topMargin = dp(22);
        root.addView(stepsTitle, stp);

        TextView steps = new TextView(this);
        steps.setText(
                "1. Sambungkan tablet ke Wi-Fi.\n" +
                "2. Tekan MULAI SERVER.\n" +
                "3. Uji alamat lokal yang muncul.\n" +
                "4. Konfigurasikan tunnel HTTPS tepercaya ke port 8080 jika perlu akses dari internet.\n" +
                "5. Buka tautan OHLI Social dari perangkat lain; jangan ekspos HTTP mentah ke internet.\n" +
                "6. Saat pengguna meminta login/daftar, buka notifikasi OTP pada perangkat server dan kirim kode secara pribadi melalui WhatsApp. Kode berlaku 5 menit dan hanya sekali pakai.\n" +
                "7. Untuk server 24 jam, izinkan aplikasi berjalan tanpa optimasi baterai, aktifkan auto-start pabrikan, dan sambungkan pengisi daya.\n" +
                "8. Koneksi publik memerlukan tunnel yang berjalan; ketika internet terputus, layanan publik juga terputus sementara."
        );
        steps.setTextColor(MUTED);
        steps.setTextSize(14);
        steps.setLineSpacing(dp(5), 1.0f);
        LinearLayout.LayoutParams stepsParams = matchWrap();
        stepsParams.topMargin = dp(10);
        root.addView(steps, stepsParams);

        TextView warning = new TextView(this);
        warning.setText("DATA: Akun, pesan, foto, Story, dan pengaturan disimpan di penyimpanan internal tablet ini dengan file cadangan. Data tetap tersimpan pada penyimpanan privat saat jaringan terputus. Server lokal dapat tetap hidup selama Android tidak menghentikan layanan; pengguna internet tidak dapat mengaksesnya ketika seluruh internet mati. Setelah jaringan pulih, tunnel yang masih berjalan harus tersambung kembali. Jangan hapus data aplikasi atau uninstall jika ingin mempertahankan akun. Buat backup berkala.");
        warning.setTextColor(Color.rgb(130, 76, 25));
        warning.setTextSize(12);
        warning.setPadding(dp(13), dp(13), dp(13), dp(13));
        warning.setBackgroundColor(Color.rgb(255, 242, 222));
        LinearLayout.LayoutParams warningParams = matchWrap();
        warningParams.topMargin = dp(20);
        root.addView(warning, warningParams);

        return scroll;
    }

    private void requestLocalAdminPin() {
        final android.content.SharedPreferences prefs = getSharedPreferences(LOCAL_ADMIN_PREFS, MODE_PRIVATE);
        final String savedHash = prefs.getString(ADMIN_PIN_HASH, "");
        final boolean firstSetup = TextUtils.isEmpty(savedHash);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8), dp(8), dp(8), dp(4));
        TextView info = new TextView(this); info.setText(firstSetup ? "Buat PIN lokal minimal 6 angka untuk membuka panel pengelola pada HP server ini. PIN hanya melindungi panel di perangkat; jangan bagikan perangkat atau PIN." : "Masukkan PIN pengelola lokal untuk membuka daftar pengguna."); info.setTextColor(MUTED); info.setTextSize(13); box.addView(info, matchWrap());
        final EditText pin = new EditText(this); pin.setSingleLine(true); pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD); pin.setHint(firstSetup ? "Buat PIN (minimal 6 angka)" : "PIN pengelola"); LinearLayout.LayoutParams pp = matchWrap(); pp.topMargin = dp(12); box.addView(pin, pp);
        final EditText confirm = new EditText(this);
        if (firstSetup) { confirm.setSingleLine(true); confirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD); confirm.setHint("Ulangi PIN"); LinearLayout.LayoutParams cp = matchWrap(); cp.topMargin = dp(8); box.addView(confirm, cp); }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(firstSetup ? "Buat PIN pengelola" : "Verifikasi pengelola").setView(box).setNegativeButton("Batal", null).setPositiveButton(firstSetup ? "Simpan PIN" : "Buka panel", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = pin.getText().toString().trim();
            if (value.length() < 6 || value.length() > 12) { pin.setError("PIN harus 6–12 angka"); return; }
            if (firstSetup) {
                if (!value.equals(confirm.getText().toString().trim())) { confirm.setError("PIN tidak sama"); return; }
                prefs.edit().putString(ADMIN_PIN_HASH, sha256(value)).apply(); dialog.dismiss(); showUserManagement();
            } else {
                if (!sha256(value).equals(savedHash)) { pin.setError("PIN salah"); return; }
                dialog.dismiss(); showUserManagement();
            }
        }));
        dialog.show();
    }

    private String sha256(String value) {
        try { byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8")); StringBuilder out = new StringBuilder(); for (byte b : hash) out.append(String.format(Locale.US, "%02x", b & 0xff)); return out.toString(); }
        catch (Exception e) { return ""; }
    }

    private void showUserManagement() {
        if (!ServerService.isRunning) { new AlertDialog.Builder(this).setTitle("Server belum aktif").setMessage("Nyalakan server dan tunggu sampai selesai memuat data, lalu buka panel pengguna lagi.").setPositiveButton("OK", null).show(); return; }
        final JSONArray list = ServerService.adminListUsersLocal();
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView intro = new TextView(this); intro.setText("Pengguna terdaftar: " + list.length() + ". Status online dihitung dari aktivitas sesi 2 menit terakhir. Penonaktifan mencabut sesi dan memblokir login; hapus akses adalah soft-delete agar data/konten tidak hilang."); intro.setTextColor(MUTED); intro.setTextSize(12); content.addView(intro, matchWrap());
        if (list.length() == 0) { TextView empty = new TextView(this); empty.setText("Belum ada akun atau data server masih dimuat."); empty.setTextColor(MUTED); empty.setTextSize(14); LinearLayout.LayoutParams ep = matchWrap(); ep.topMargin = dp(14); content.addView(empty, ep); }
        for (int i = 0; i < list.length(); i++) {
            final JSONObject user = list.optJSONObject(i); if (user == null) continue;
            final String phone = user.optString("phone", "");
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12), dp(10), dp(12), dp(10)); card.setBackgroundColor(Color.rgb(244, 247, 246)); LinearLayout.LayoutParams cp = matchWrap(); cp.topMargin = dp(8); content.addView(card, cp);
            TextView title = new TextView(this); title.setText(user.optString("name", "Pengguna") + (user.optBoolean("online") ? "  ·  ● ONLINE" : "  ·  Offline")); title.setTextColor(INK); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setTextSize(14); card.addView(title, matchWrap());
            TextView detail = new TextView(this); detail.setText("+" + phone + "  ·  @" + user.optString("username", "") + "  ·  Status: " + user.optString("accessStatus", "active") + (user.optLong("suspendedUntil", 0) > System.currentTimeMillis() ? " sampai " + new SimpleDateFormat("dd MMM HH:mm", new Locale("id", "ID")).format(new Date(user.optLong("suspendedUntil"))) : "")); detail.setTextColor(MUTED); detail.setTextSize(11); LinearLayout.LayoutParams dpms = matchWrap(); dpms.topMargin = dp(4); card.addView(detail, dpms);
            Button actions = makeButton("KELOLA AKUN", Color.rgb(231, 237, 255), Color.rgb(40, 57, 110)); LinearLayout.LayoutParams ap = matchWrap(); ap.topMargin = dp(8); card.addView(actions, ap); actions.setOnClickListener(v -> showUserActions(phone, user.optString("name", "Pengguna")));
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(content); AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Pengguna OHLI · Kontrol lokal").setView(scroll).setPositiveButton("Tutup", null).setNeutralButton("Muat ulang", (d,w) -> showUserManagement()).create(); dialog.show(); if (dialog.getWindow() != null) dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, dp(620));
    }

    private void showUserActions(final String phone, final String name) {
        final String[] actions = new String[] { "Batasi 1 hari", "Batasi 7 hari", "Nonaktifkan akun", "Cabut akses akun (soft-delete)", "Pulihkan akses" };
        new AlertDialog.Builder(this).setTitle("Kelola " + name).setItems(actions, (dialog, which) -> {
            final String action = which == 0 ? "suspend1d" : which == 1 ? "suspend7d" : which == 2 ? "disable" : which == 3 ? "softdelete" : "restore";
            String description = which == 0 ? "Akun dibatasi selama 24 jam dan sesi aktif dicabut." : which == 1 ? "Akun dibatasi selama 7 hari dan sesi aktif dicabut." : which == 2 ? "Akun dinonaktifkan sampai pengelola memulihkannya." : which == 3 ? "Akses akun dicabut, tetapi konten dan riwayat tetap disimpan untuk pemulihan/audit." : "Akses akun dipulihkan.";
            new AlertDialog.Builder(this).setTitle("Konfirmasi tindakan").setMessage(description + "\n\nNomor: +" + phone).setNegativeButton("Batal", null).setPositiveButton("Lanjutkan", (d,w) -> {
                boolean ok = ServerService.moderateUserLocal(phone, action);
                Toast.makeText(this, ok ? "Akses akun diperbarui." : "Gagal memperbarui akun. Pastikan server aktif.", Toast.LENGTH_LONG).show();
                if (ok) showUserManagement();
            }).show();
        }).show();
    }

    private void showTunnelSettings() {
        final android.content.SharedPreferences prefs = getSharedPreferences(LOCAL_ADMIN_PREFS, MODE_PRIVATE);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12), dp(8), dp(12), dp(4));
        TextView explanation = new TextView(this); explanation.setText("Sasaran tunnel: http://127.0.0.1:" + ServerService.PORT + "\n\nQuick Tunnel Cloudflare gratis memberi URL sementara dan berubah saat proses dihentikan. Untuk link stabil gunakan Named Tunnel pada akun Cloudflare dan domain yang kamu kelola. Tunnel harus memiliki proses cloudflared yang berjalan dan internet aktif. Aplikasi ini belum membawa binary cloudflared Android, jadi panel ini menyimpan/mengelola link publik tetapi tidak mengaku menyalakan tunnel otomatis."); explanation.setTextColor(MUTED); explanation.setTextSize(13); explanation.setLineSpacing(dp(3), 1f); box.addView(explanation, matchWrap());
        final EditText url = new EditText(this); url.setSingleLine(true); url.setHint("https://nama-domain-atau-trycloudflare.com"); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); url.setText(prefs.getString(PUBLIC_URL_KEY, "")); LinearLayout.LayoutParams up = matchWrap(); up.topMargin = dp(12); box.addView(url, up);
        TextView command = new TextView(this); command.setText("Quick Tunnel (sementara):\ncloudflared tunnel --url http://localhost:8080\n\nJangan gunakan untuk jaminan uptime produksi."); command.setTextColor(INK); command.setTextSize(12); command.setPadding(dp(10), dp(10), dp(10), dp(10)); command.setBackgroundColor(Color.rgb(244, 247, 246)); LinearLayout.LayoutParams cm = matchWrap(); cm.topMargin = dp(10); box.addView(command, cm);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Tunnel & link publik").setView(box).setNegativeButton("Tutup", null).setNeutralButton("Salin perintah", (d,w) -> copyText("cloudflared tunnel --url http://localhost:8080", "Perintah Quick Tunnel disalin.")).setPositiveButton("Simpan link", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { String value = url.getText().toString().trim(); if (!value.isEmpty() && !value.matches("(?i)^https://[a-z0-9.-]+(?::[0-9]{1,5})?/?$")) { url.setError("Masukkan URL HTTPS yang valid"); return; } prefs.edit().putString(PUBLIC_URL_KEY, value).apply(); dialog.dismiss(); Toast.makeText(this, value.isEmpty() ? "Link publik dihapus." : "Link publik tersimpan.", Toast.LENGTH_SHORT).show(); if (!value.isEmpty()) showPublicLinkActions(value); }));
        dialog.show();
    }

    private void showPublicLinkActions(final String value) {
        new AlertDialog.Builder(this).setTitle("Link publik OHLI").setMessage(value + "\n\nPastikan link masih aktif di Cloudflare sebelum dibagikan.").setNegativeButton("Tutup", null).setNeutralButton("Salin", (d,w) -> copyText(value, "Link publik disalin.")).setPositiveButton("Buka", (d,w) -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value))); } catch (Exception e) { Toast.makeText(this, "Tidak dapat membuka link.", Toast.LENGTH_SHORT).show(); } }).show();
    }

    private void copyText(String text, String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("OHLI", text));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showKeepAliveGuide() {
        new AlertDialog.Builder(this).setTitle("Server 24 jam · layar mati").setMessage("Server berjalan sebagai foreground service, memakai CPU wake lock, START_STICKY, dan mencoba aktif lagi setelah reboot atau service dihentikan sistem. Supaya peluang hidup 24 jam lebih baik:\n\n1. Buka pengaturan optimasi baterai Android dan izinkan OHLI berjalan tanpa optimasi.\n2. Pada Xiaomi/OPPO/Vivo/Realme, aktifkan Auto-start dan kunci OHLI di Recent Apps jika tersedia.\n3. Sambungkan charger dan gunakan Wi-Fi/router stabil.\n4. Jangan tekan Paksa berhenti dan jangan hapus data aplikasi.\n\nKetika Wi-Fi dan data seluler sama-sama mati, server lokal masih dapat menyimpan data, tetapi pengguna internet tidak dapat terhubung. Tunnel akan pulih hanya setelah proses cloudflared masih hidup dan koneksi internet kembali. Tidak ada aplikasi Android yang dapat menjamin uptime 100% jika OS mematikan proses atau perangkat kehabisan daya.").setNegativeButton("Tutup", null).setPositiveButton("Buka pengaturan baterai", (d,w) -> { try { if (android.os.Build.VERSION.SDK_INT >= 23) startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); else startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); } catch (Exception e) { Toast.makeText(this, "Pengaturan baterai tidak tersedia.", Toast.LENGTH_SHORT).show(); } }).show();
    }

    private void createDataBackup() {
        Toast.makeText(this, "Menyiapkan backup data OHLI…", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            File backupRoot = getExternalFilesDir("OHLI-Backups"); if (backupRoot == null) backupRoot = new File(getFilesDir(), "OHLI-Backups");
            if (!backupRoot.exists()) backupRoot.mkdirs();
            File backup = new File(backupRoot, "OHLI-backup-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".zip");
            boolean ok = false; String error = "";
            try { zipDirectory(getFilesDir(), backup, backupRoot); ok = backup.exists() && backup.length() > 0; } catch (Exception e) { error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
            if (ok) pruneBackups(backupRoot);
            final boolean success = ok; final String path = backup.getAbsolutePath(); final String failure = error;
            runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(success ? "Backup selesai" : "Backup gagal").setMessage(success ? "Salinan data lokal disimpan di:\n" + path + "\n\nSimpan salinan ini di perangkat lain juga. Backup internal tidak melindungi dari kerusakan atau kehilangan HP." : "Backup tidak selesai: " + failure).setPositiveButton("OK", null).show());
        }, "OHLI-Backup").start();
    }

    private void zipDirectory(File directory, File output, File excludedRoot) throws Exception {
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output)); zip.setLevel(Deflater.BEST_SPEED);
        try { addDirectoryToZip(directory, directory, output, excludedRoot, zip); } finally { zip.close(); }
    }
    private void addDirectoryToZip(File root, File current, File output, File excludedRoot, ZipOutputStream zip) throws Exception {
        File[] children = current.listFiles(); if (children == null) return;
        byte[] buffer = new byte[65536];
        for (File child : children) {
            if (child.equals(excludedRoot) || child.equals(output)) continue;
            if (child.isDirectory()) { addDirectoryToZip(root, child, output, excludedRoot, zip); continue; }
            if (!child.isFile() || child.getName().endsWith(".tmp")) continue;
            String name = root.toURI().relativize(child.toURI()).getPath(); ZipEntry entry = new ZipEntry(name); zip.putNextEntry(entry);
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(child)); try { int count; while ((count = in.read(buffer)) != -1) zip.write(buffer, 0, count); } finally { in.close(); }
            zip.closeEntry();
        }
    }
    private void pruneBackups(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".zip")); if (files == null || files.length <= 3) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed()); for (int i = 3; i < files.length; i++) files[i].delete();
    }

    private void refreshInboxBadge() {
        if (inboxButton == null) return;
        File file = new File(getFilesDir(), "ohli-server-inbox.json");
        long modified = file.exists() ? file.lastModified() : 0L;
        if (modified == inboxFileModified) return;
        inboxFileModified = modified;
        int unread = 0;
        try {
            JSONArray items = new JSONArray(ServerService.readServerInbox(this));
            long now = System.currentTimeMillis();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && !item.optBoolean("read", false)
                        && (item.optLong("expiresAt", 0) == 0 || item.optLong("expiresAt", 0) > now)) unread++;
            }
        } catch (Exception ignored) { }
        inboxButton.setText("🔔 PUSAT NOTIFIKASI (" + unread + ")");
    }

    private void showServerInbox() {
        JSONArray items;
        try { items = new JSONArray(ServerService.readServerInbox(this)); }
        catch (Exception e) { items = new JSONArray(); }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        long now = System.currentTimeMillis();
        if (items.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Belum ada notifikasi server. Permintaan login dan pendaftaran akan tersimpan di sini.");
            empty.setTextColor(MUTED); empty.setTextSize(14); empty.setPadding(dp(8), dp(18), dp(8), dp(18));
            list.addView(empty, matchWrap());
        } else {
            for (int i = items.length() - 1; i >= 0; i--) {
                final JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(13), dp(12), dp(13), dp(12));
                card.setBackgroundColor(Color.rgb(244, 247, 246));
                LinearLayout.LayoutParams cp = matchWrap(); cp.bottomMargin = dp(9); list.addView(card, cp);
                TextView title = new TextView(this); title.setText(item.optString("title", "Notifikasi OHLI")); title.setTextColor(INK); title.setTextSize(15); title.setTypeface(null, android.graphics.Typeface.BOLD); card.addView(title, matchWrap());
                TextView message = new TextView(this); message.setText(item.optString("message", "")); message.setTextColor(MUTED); message.setTextSize(13); LinearLayout.LayoutParams mp = matchWrap(); mp.topMargin = dp(5); card.addView(message, mp);
                long time = item.optLong("time", 0);
                TextView date = new TextView(this); date.setText(time > 0 ? new SimpleDateFormat("dd MMM yyyy · HH:mm", new Locale("id", "ID")).format(new Date(time)) : "Waktu tidak tersedia"); date.setTextColor(MUTED); date.setTextSize(11); LinearLayout.LayoutParams dpms = matchWrap(); dpms.topMargin = dp(5); card.addView(date, dpms);
                final String code = item.optString("otp", "");
                long expiry = item.optLong("expiresAt", 0);
                if (code.matches("[0-9]{6}") && expiry > now) {
                    TextView codeText = new TextView(this); codeText.setText("Kode OTP: " + code + " · berlaku sampai " + new SimpleDateFormat("HH:mm:ss", new Locale("id", "ID")).format(new Date(expiry))); codeText.setTextColor(INK); codeText.setTextSize(15); codeText.setTypeface(null, android.graphics.Typeface.BOLD); LinearLayout.LayoutParams otpParams = matchWrap(); otpParams.topMargin = dp(8); card.addView(codeText, otpParams);
                    Button copy = makeButton("SALIN OTP", GREEN, WHITE); LinearLayout.LayoutParams copyParams = matchWrap(); copyParams.topMargin = dp(7); card.addView(copy, copyParams);
                    copy.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("OTP OHLI", code)); Toast.makeText(MainActivity.this, "Kode OTP disalin. Kirim hanya ke pemilik nomor.", Toast.LENGTH_LONG).show(); } });
                } else if ("auth_otp".equals(item.optString("type"))) {
                    TextView expired = new TextView(this); expired.setText("OTP sudah kedaluwarsa. Minta pengguna mengirim permintaan baru."); expired.setTextColor(Color.rgb(170, 68, 55)); expired.setTextSize(12); LinearLayout.LayoutParams ep = matchWrap(); ep.topMargin = dp(6); card.addView(expired, ep);
                }
            }
        }
        ScrollView scroll = new ScrollView(this); scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Pusat Notifikasi Server").setView(scroll).setPositiveButton("Tutup", null).create();
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, dp(540));
        ServerService.markServerInboxRead(this);
        inboxFileModified = Long.MIN_VALUE;
        refreshInboxBadge();
    }

    private Button makeButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setTextColor(foreground);
        button.setBackgroundColor(background);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(7), dp(12), dp(7));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String getServerUrl() {
        String ip = getLocalIpAddress();
        if (TextUtils.isEmpty(ip) || "0.0.0.0".equals(ip)) return null;
        return "http://" + ip + ":" + ServerService.PORT + "/";
    }

    private String getLocalIpAddress() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifi.isWifiEnabled()) {
                int address = wifi.getConnectionInfo().getIpAddress();
                if (address != 0) {
                    return String.format(Locale.US, "%d.%d.%d.%d", (address & 0xff), (address >> 8 & 0xff), (address >> 16 & 0xff), (address >> 24 & 0xff));
                }
            }
        } catch (Exception ignored) { }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') < 0) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void refreshStatus() {
        boolean running = ServerService.isRunning;
        String url = getServerUrl();
        if (running) {
            statusText.setText("●  SERVER AKTIF");
            statusText.setTextColor(GREEN);
            addressText.setText(url == null ? "Server aktif · cek koneksi Wi-Fi" : url);
            detailText.setText("Port " + ServerService.PORT + " · Buka alamat ini dari HP pada Wi-Fi yang sama");
            startButton.setEnabled(false);
            startButton.setAlpha(0.55f);
            stopButton.setEnabled(true);
        } else {
            statusText.setText("○  SERVER BELUM AKTIF");
            statusText.setTextColor(MUTED);
            String error = ServerService.lastError;
            addressText.setText(url == null ? "IP belum terbaca · sambungkan Wi-Fi" : url);
            detailText.setText(!TextUtils.isEmpty(error) ? "Error terakhir: " + error : "Port " + ServerService.PORT + " · Server berhenti sampai kamu menyalakannya");
            startButton.setEnabled(true);
            startButton.setAlpha(1.0f);
            stopButton.setEnabled(false);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        handler.removeCallbacks(refreshRunnable);
        handler.post(refreshRunnable);
    }

    @Override protected void onPause() {
        foreground = false;
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }
}
