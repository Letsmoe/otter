package stream

import (
	"net/url"
	"testing"
)

func TestHighestBandwidthVariant(t *testing.T) {
	base, _ := url.Parse("https://cdn.example/movie/master.m3u8")
	master := `#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,AVERAGE-BANDWIDTH=9000000,RESOLUTION=640x360
360p/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
1080p/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
720p/index.m3u8
`
	if got := highestBandwidthVariant(master, base); got != "https://cdn.example/movie/1080p/index.m3u8" {
		t.Fatalf("got %q", got)
	}
	media := "#EXTM3U\n#EXTINF:6,\nseg1.ts\n"
	if got := highestBandwidthVariant(media, base); got != "" {
		t.Fatalf("media playlist should have no variant, got %q", got)
	}
}
