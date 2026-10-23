package dev.neoworks.otter;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Let the video player go fullscreen (Capacitor's default client refuses).
        bridge.getWebView().setWebChromeClient(new FullscreenChromeClient(bridge));
    }
}
