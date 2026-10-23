package dev.neoworks.otter;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebChromeClient;

/**
 * Makes the web Fullscreen API work. Capacitor's chrome client rejects every
 * fullscreen request, so the player's fullscreen button would do nothing; this
 * one lays the fullscreen view over the activity and hides the system bars.
 */
public class FullscreenChromeClient extends BridgeWebChromeClient {

    private final Activity activity;
    private View fullscreenView;
    private CustomViewCallback fullscreenCallback;

    public FullscreenChromeClient(Bridge bridge) {
        super(bridge);
        this.activity = bridge.getActivity();
    }

    /** Shows the element the page asked to make fullscreen over everything else. */
    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreenView = view;
        fullscreenCallback = callback;
        decorView().addView(
            view,
            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        );
        systemBars().hide(WindowInsetsCompat.Type.systemBars());
    }

    /** Returns to the regular web view once the page leaves fullscreen. */
    @Override
    public void onHideCustomView() {
        if (fullscreenView == null) return;
        decorView().removeView(fullscreenView);
        fullscreenView = null;
        systemBars().show(WindowInsetsCompat.Type.systemBars());
        fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
    }

    /** The window's root view, which the fullscreen view is layered into. */
    private FrameLayout decorView() {
        return (FrameLayout) activity.getWindow().getDecorView();
    }

    /** Controls the status and navigation bars; swiping from an edge reveals them briefly. */
    private WindowInsetsControllerCompat systemBars() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), decorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        return controller;
    }
}
