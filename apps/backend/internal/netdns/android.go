package netdns

import (
	"os"
	"strconv"

	"github.com/wlynxg/anet"
)

// ConfigureAndroidVersion tells anet the Android version (ANDROID_SDK_INT,
// set by the app). Without cgo it can't detect it, and on Android 11+ only
// its version-specific path may list network interfaces.
func ConfigureAndroidVersion() {
	sdkInt, err := strconv.Atoi(os.Getenv("ANDROID_SDK_INT"))
	if err != nil || sdkInt <= 0 {
		return
	}
	anet.SetAndroidVersion(androidRelease(sdkInt))
}

// androidRelease maps an SDK level to the release number anet expects;
// only "11 or later" matters (SDK 30).
func androidRelease(sdkInt int) uint {
	if sdkInt >= 30 {
		return 11
	}
	return 10
}
