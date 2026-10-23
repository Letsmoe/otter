package dev.neoworks.otter;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the Go backend on the phone. The binary ships as a native library
 * (libotter.so, built by `bun run build:backend`) because the app's native
 * library folder is the only place Android lets an app execute files from.
 * It listens on loopback only; the web client reaches it at BASE_URL
 * (mirrored in apps/web/src/lib/server.ts).
 */
public final class EmbeddedBackend {

    private static final String TAG = "OtterBackend";
    static final int PORT = 47832;
    static final String BASE_URL = "http://127.0.0.1:" + PORT;
    /**
     * Asked before the network's own DNS servers: provider resolvers block
     * streaming sites (e.g. the German CUII list) by answering with a
     * landing page. The network's servers stay as the fallback.
     */
    private static final String PUBLIC_DNS_SERVERS = "1.1.1.1,8.8.8.8";
    /** Gives up restarting after this many crashes in a row. */
    private static final int MAX_RESTARTS = 5;
    private static final String PREFERENCES = "embedded-backend";
    private static final String SEEDED_UPDATE_TIME = "seededUpdateTime";

    private final Context context;
    private final File dataDir;
    private final File seedDir;
    private final File dnsFile;
    private int restarts = 0;

    EmbeddedBackend(Context context) {
        this.context = context.getApplicationContext();
        this.dataDir = new File(this.context.getFilesDir(), "otter");
        this.seedDir = new File(this.context.getFilesDir(), "plugin-seed");
        this.dnsFile = new File(dataDir, "dns-servers");
    }

    /** Prepares the plugin seed and DNS file, then starts the backend on a background thread. */
    void start() {
        new Thread(this::prepareAndRun, "otter-backend").start();
    }

    /** Runs the backend, restarting it when it crashes. */
    private void prepareAndRun() {
        try {
            dataDir.mkdirs();
            refreshPluginSeed();
            watchDnsServers();
        } catch (IOException error) {
            Log.e(TAG, "preparing backend failed", error);
            return;
        }
        while (restarts <= MAX_RESTARTS) {
            int exitCode = runOnce();
            restarts++;
            Log.e(TAG, "backend exited with " + exitCode + ", restart " + restarts + "/" + MAX_RESTARTS);
            sleep(1000L * restarts);
        }
    }

    /** Starts the backend process and blocks until it exits, forwarding its log to logcat. */
    private int runOnce() {
        String binary = context.getApplicationInfo().nativeLibraryDir + "/libotter.so";
        ProcessBuilder builder = new ProcessBuilder(binary).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.put("DATA_DIR", dataDir.getAbsolutePath());
        environment.put("HOST", "127.0.0.1");
        environment.put("PORT", String.valueOf(PORT));
        environment.put("PUBLIC_URL", BASE_URL);
        environment.put("PLUGIN_DIR", seedDir.getAbsolutePath());
        environment.put("DNS_SERVERS_FILE", dnsFile.getAbsolutePath());
        environment.put("DNS_SERVERS", PUBLIC_DNS_SERVERS);
        // Without cgo the backend can't detect the Android version, which it
        // needs to list network interfaces (WebRTC, VPN check) on Android 11+.
        environment.put("ANDROID_SDK_INT", String.valueOf(android.os.Build.VERSION.SDK_INT));
        // Go's os.TempDir defaults to /data/local/tmp, which apps can't write.
        environment.put("TMPDIR", context.getCacheDir().getAbsolutePath());
        try {
            Process process = builder.start();
            forwardLog(process.getInputStream());
            return process.waitFor();
        } catch (IOException error) {
            Log.e(TAG, "starting backend failed", error);
            return -1;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** Copies each backend output line to logcat. */
    private static void forwardLog(InputStream output) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(output, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            Log.i(TAG, line);
        }
    }

    /**
     * Extracts the plugin bundles shipped in the APK (assets/plugins) once per
     * install or update of the app. The backend loads them from there.
     */
    private void refreshPluginSeed() throws IOException {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        long updateTime = packageUpdateTime();
        if (seedDir.isDirectory() && preferences.getLong(SEEDED_UPDATE_TIME, 0) == updateTime) {
            return;
        }
        deleteRecursively(seedDir);
        copyAssets(context.getAssets(), "plugins", seedDir);
        preferences.edit().putLong(SEEDED_UPDATE_TIME, updateTime).apply();
    }

    /** When the app was last installed or updated. */
    private long packageUpdateTime() {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException error) {
            return 0;
        }
    }

    /** Copies an asset folder tree to target. */
    private static void copyAssets(AssetManager assets, String path, File target) throws IOException {
        String[] children = assets.list(path);
        if (children == null || children.length == 0) {
            copyAsset(assets, path, target);
            return;
        }
        target.mkdirs();
        for (String child : children) {
            copyAssets(assets, path + "/" + child, new File(target, child));
        }
    }

    /** Copies one asset file to target. */
    private static void copyAsset(AssetManager assets, String path, File target) throws IOException {
        try (InputStream input = assets.open(path); OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    /** Deletes a file or folder tree. */
    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /**
     * Keeps dnsFile listing the current network's DNS servers. Go can't ask
     * Android for them (pure-Go build, no resolv.conf); the backend re-reads the
     * file on every lookup, so Wi-Fi/mobile/VPN switches take effect at once.
     */
    private void watchDnsServers() {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        Network active = connectivity.getActiveNetwork();
        if (active != null) {
            writeDnsServers(connectivity.getLinkProperties(active));
        }
        connectivity.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                writeDnsServers(properties);
            }
        });
    }

    /** Writes the public DNS servers, then the network's, to dnsFile, one per line. */
    private void writeDnsServers(LinkProperties properties) {
        if (properties == null) {
            return;
        }
        List<String> servers = new ArrayList<>(List.of(PUBLIC_DNS_SERVERS.split(",")));
        for (InetAddress server : properties.getDnsServers()) {
            servers.add(server.getHostAddress());
        }
        File temporary = new File(dataDir, ".dns-servers.tmp");
        try (OutputStream output = new FileOutputStream(temporary)) {
            output.write(String.join("\n", servers).getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            Log.w(TAG, "writing DNS servers failed", error);
            return;
        }
        if (!temporary.renameTo(dnsFile)) {
            Log.w(TAG, "replacing DNS server file failed");
        }
    }

    /** Sleeps, ignoring interruption. */
    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
