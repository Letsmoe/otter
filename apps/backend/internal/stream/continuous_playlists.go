package stream

import (
	"context"
	"strconv"
	"sync"
	"time"
)

// continuousPlaylistLifetime is how long a loaded playlist is reused. The
// phone probes a continuous stream right before handing it to the TV, and
// Philips TVs give up on a HEAD that takes longer than about a second, so
// the TV's own request must not fetch the playlists over the VPN again.
const continuousPlaylistLifetime = 5 * time.Minute

type loadedPlaylist struct {
	playlist mediaPlaylist
	loadedAt time.Time
}

// continuousPlaylists remembers the media playlists continuous streams were
// served from, by session and manifest URL. The zero value is ready to use.
type continuousPlaylists struct {
	mutex  sync.Mutex
	loaded map[string]loadedPlaylist
}

func (playlists *continuousPlaylists) get(key string) (mediaPlaylist, bool) {
	playlists.mutex.Lock()
	defer playlists.mutex.Unlock()
	entry, found := playlists.loaded[key]
	if !found || time.Since(entry.loadedAt) > continuousPlaylistLifetime {
		return mediaPlaylist{}, false
	}
	return entry.playlist, true
}

func (playlists *continuousPlaylists) put(key string, playlist mediaPlaylist) {
	playlists.mutex.Lock()
	defer playlists.mutex.Unlock()
	if playlists.loaded == nil {
		playlists.loaded = map[string]loadedPlaylist{}
	}
	for existingKey, entry := range playlists.loaded {
		if time.Since(entry.loadedAt) > continuousPlaylistLifetime {
			delete(playlists.loaded, existingKey)
		}
	}
	playlists.loaded[key] = loadedPlaylist{playlist: playlist, loadedAt: time.Now()}
}

// continuousPlaylist loads the media playlist of a continuous stream, or
// reuses the one loaded for the same session, manifest and height limit
// moments ago.
func (p *Proxy) continuousPlaylist(ctx context.Context, sessionID string, manifestURL string, headers map[string]string, maxHeight int) (mediaPlaylist, error) {
	key := sessionID + " " + strconv.Itoa(maxHeight) + " " + manifestURL
	if playlist, found := p.playlists.get(key); found {
		return playlist, nil
	}
	playlist, err := p.loadMediaPlaylist(ctx, manifestURL, headers, maxHeight)
	if err != nil {
		return mediaPlaylist{}, err
	}
	p.playlists.put(key, playlist)
	return playlist, nil
}
