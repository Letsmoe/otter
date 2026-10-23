package dev.neoworks.otter;

import android.app.Application;

/** Starts the on-device backend once per app process, before any activity. */
public class OtterApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        new EmbeddedBackend(this).start();
    }
}
