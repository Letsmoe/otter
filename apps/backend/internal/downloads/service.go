package downloads

import (
	"context"
	"errors"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/neoworks/otter/internal/library"
	"github.com/neoworks/otter/internal/plugins"
	"github.com/neoworks/otter/internal/stream"
)

const (
	// Links tried before a download fails.
	linkAttempts = 5
	// Progress is written at most this often.
	progressInterval = time.Second
	// Downloads running at once; a few leave bandwidth for playback.
	parallelDownloads = 3
	queueSize         = 256
)

var (
	ErrNotDownloadable  = errors.New("only movies and episodes can be downloaded")
	ErrStreamNotOfTitle = errors.New("that source belongs to another title")
)

type resolver interface {
	Resolve(ctx context.Context, providerURL string) (*plugins.ResolveResult, error)
}

type downloader interface {
	Download(ctx context.Context, result *plugins.ResolveResult, resumeKey string, progress func(pct float64, rx, total int64)) (stream.Downloaded, error)
}

type Service struct {
	Store    *Store
	library  *library.Service
	resolver resolver
	cache    downloader
	queue    chan int
	// exporter is nil when finished downloads are not mirrored into a folder.
	exporter *Exporter

	cancelMutex sync.Mutex
	// cancels stops the running download of a row when it is deleted.
	cancels map[int]context.CancelFunc
}

func NewService(store *Store, libraryService *library.Service, streamResolver resolver, cache downloader) *Service {
	return &Service{
		Store:    store,
		library:  libraryService,
		resolver: streamResolver,
		cache:    cache,
		queue:    make(chan int, queueSize),
		cancels:  map[int]context.CancelFunc{},
	}
}

// SetExporter mirrors finished downloads into the exporter's folder.
func (s *Service) SetExporter(exporter *Exporter) {
	s.exporter = exporter
}

// Run works through the queue with parallelDownloads workers until ctx ends,
// starting with downloads left over from the last run.
func (s *Service) Run(ctx context.Context) {
	s.requeueUnfinished(ctx)
	go s.exportFinished(ctx)
	var workers sync.WaitGroup
	for range parallelDownloads {
		workers.Add(1)
		go func() {
			defer workers.Done()
			s.work(ctx)
		}()
	}
	workers.Wait()
}

func (s *Service) work(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case id := <-s.queue:
			s.process(ctx, id)
		}
	}
}

func (s *Service) requeueUnfinished(ctx context.Context) {
	unfinished, err := s.Store.Unfinished(ctx)
	if err != nil {
		log.Printf("downloads: read unfinished: %v", err)
		return
	}
	for _, download := range unfinished {
		s.enqueue(download.ID)
	}
}

// exportFinished exports downloads finished before exporting existed or
// whose file was removed from the folder.
func (s *Service) exportFinished(ctx context.Context) {
	if s.exporter == nil {
		return
	}
	finished, err := s.Store.Finished(ctx)
	if err != nil {
		log.Printf("downloads: read finished: %v", err)
		return
	}
	for _, download := range finished {
		s.export(ctx, download)
	}
}

func (s *Service) export(ctx context.Context, download Download) {
	if s.exporter == nil {
		return
	}
	if err := s.exporter.Export(ctx, download); err != nil {
		log.Printf("downloads: export %d: %v", download.ID, err)
	}
}

func (s *Service) enqueue(id int) {
	select {
	case s.queue <- id:
	default:
		log.Printf("downloads: queue full, download %d waits for the next start", id)
	}
}

// Start queues a movie or episode for the profile, from the given link or,
// with streamID 0, the best ones.
func (s *Service) Start(ctx context.Context, profileID int, ref library.MediaRef, streamID int) (*Download, error) {
	seriesID, err := s.seriesOf(ctx, ref)
	if err != nil {
		return nil, err
	}
	if err := s.checkStream(ctx, ref, streamID); err != nil {
		return nil, err
	}
	download, err := s.Store.Queue(ctx, profileID, ref, seriesID, streamID)
	if err != nil || download == nil {
		return download, err
	}
	if download.Status == StatusQueued {
		s.enqueue(download.ID)
	}
	return download, nil
}

// checkStream makes sure a picked link belongs to the title.
func (s *Service) checkStream(ctx context.Context, ref library.MediaRef, streamID int) error {
	if streamID == 0 {
		return nil
	}
	stream, err := s.library.Store.Stream(ctx, streamID)
	if err != nil {
		return err
	}
	if stream == nil || stream.MediaType != ref.MediaType || stream.MediaID != ref.MediaID {
		return ErrStreamNotOfTitle
	}
	return nil
}

func (s *Service) seriesOf(ctx context.Context, ref library.MediaRef) (int, error) {
	switch ref.MediaType {
	case library.MediaTypeMovie:
		return 0, nil
	case library.MediaTypeEpisode:
		episode, err := s.library.Store.Episode(ctx, ref.MediaID)
		if err != nil {
			return 0, err
		}
		if episode == nil {
			return 0, fmt.Errorf("episode %d not found", ref.MediaID)
		}
		return episode.SeriesID, nil
	default:
		return 0, ErrNotDownloadable
	}
}

// Delete removes a download and stops it if it is running. Its blobs are no
// longer pinned and leave with the next cache eviction; its exported file
// goes unless another profile downloaded the same file.
func (s *Service) Delete(ctx context.Context, id int) error {
	s.cancelMutex.Lock()
	if cancel, running := s.cancels[id]; running {
		cancel()
	}
	s.cancelMutex.Unlock()
	download, err := s.Store.Get(ctx, id)
	if err != nil {
		return err
	}
	if err := s.Store.Delete(ctx, id); err != nil {
		return err
	}
	if download != nil {
		s.removeExport(ctx, *download)
	}
	return nil
}

func (s *Service) removeExport(ctx context.Context, download Download) {
	if s.exporter == nil || download.Status != StatusDone {
		return
	}
	inUse, err := s.Store.FileInUse(ctx, download.FileURL, download.ID)
	if err != nil || inUse {
		return
	}
	if err := s.exporter.Remove(ctx, download); err != nil {
		log.Printf("downloads: remove export %d: %v", download.ID, err)
	}
}

func (s *Service) process(ctx context.Context, id int) {
	download, err := s.Store.Get(ctx, id)
	if err != nil || download == nil || download.Status == StatusDone {
		return
	}
	jobContext, cancel := context.WithCancel(ctx)
	defer cancel()
	// A retry can queue an id that another worker is still running.
	if !s.trackCancel(id, cancel) {
		return
	}
	defer s.untrackCancel(id)

	if err := s.Store.MarkDownloading(ctx, id); err != nil {
		log.Printf("downloads: %d: %v", id, err)
		return
	}
	saved, err := s.fetch(jobContext, *download)
	if jobContext.Err() != nil {
		return
	}
	if err != nil {
		log.Printf("downloads: %d failed: %v", id, err)
		_ = s.Store.MarkFailed(ctx, id, err.Error())
		return
	}
	if err := s.Store.MarkDone(ctx, id, saved.URL, saved.MimeType); err != nil {
		log.Printf("downloads: %d: %v", id, err)
		return
	}
	download.FileURL = saved.URL
	download.MimeType = saved.MimeType
	s.export(ctx, *download)
}

// trackCancel registers a running download; false when it already runs.
func (s *Service) trackCancel(id int, cancel context.CancelFunc) bool {
	s.cancelMutex.Lock()
	defer s.cancelMutex.Unlock()
	if _, running := s.cancels[id]; running {
		return false
	}
	s.cancels[id] = cancel
	return true
}

func (s *Service) untrackCancel(id int) {
	s.cancelMutex.Lock()
	defer s.cancelMutex.Unlock()
	delete(s.cancels, id)
}

// fetch downloads the picked link, or tries the title's best links until one
// downloads.
func (s *Service) fetch(ctx context.Context, download Download) (stream.Downloaded, error) {
	if download.StreamID != 0 {
		return s.fetchPicked(ctx, download)
	}
	links, err := s.links(ctx, download.Ref())
	if err != nil {
		return stream.Downloaded{}, err
	}
	if len(links) == 0 {
		return stream.Downloaded{}, errors.New("no sources found")
	}
	var lastError error
	for _, link := range firstLinks(activeFirst(links, download.ActiveStreamID)) {
		saved, err := s.fetchLink(ctx, download.ID, link)
		if err == nil {
			return saved, nil
		}
		if ctx.Err() != nil {
			return stream.Downloaded{}, ctx.Err()
		}
		lastError = err
	}
	return stream.Downloaded{}, fmt.Errorf("no source could be downloaded: %w", lastError)
}

func (s *Service) fetchPicked(ctx context.Context, download Download) (stream.Downloaded, error) {
	link, err := s.library.Store.Stream(ctx, download.StreamID)
	if err != nil {
		return stream.Downloaded{}, err
	}
	if link == nil {
		return stream.Downloaded{}, errors.New("the chosen source is gone; pick another")
	}
	return s.fetchLink(ctx, download.ID, *link)
}

// activeFirst moves the link an interrupted run was saving to the front, so
// the download continues from it.
func activeFirst(links []library.Stream, activeStreamID int) []library.Stream {
	for index, link := range links {
		if link.ID != activeStreamID {
			continue
		}
		ordered := append([]library.Stream{link}, links[:index]...)
		return append(ordered, links[index+1:]...)
	}
	return links
}

func firstLinks(links []library.Stream) []library.Stream {
	if len(links) > linkAttempts {
		return links[:linkAttempts]
	}
	return links
}

// links returns the stored links, best first, looking them up when there are none.
func (s *Service) links(ctx context.Context, ref library.MediaRef) ([]library.Stream, error) {
	stored, err := s.library.Store.Streams(ctx, ref)
	if err != nil || len(stored) > 0 {
		return stored, err
	}
	if err := s.library.FindStreams(ctx, ref); err != nil {
		return nil, err
	}
	return s.library.Store.Streams(ctx, ref)
}

func (s *Service) fetchLink(ctx context.Context, id int, link library.Stream) (stream.Downloaded, error) {
	result, err := s.resolver.Resolve(ctx, link.URL)
	if plugins.IsDeadLink(err) {
		_ = s.library.Store.ForgetDeadLink(ctx, link.URL)
		return stream.Downloaded{}, err
	}
	if err != nil {
		_ = s.library.Store.MarkPlayable(ctx, link.URL, false, 0, "")
		return stream.Downloaded{}, err
	}
	if err := s.Store.SetActiveStream(ctx, id, link.ID); err != nil {
		return stream.Downloaded{}, err
	}
	return s.cache.Download(ctx, result, link.URL, s.progressWriter(ctx, id))
}

// progressWriter stores download progress, throttled to progressInterval.
func (s *Service) progressWriter(ctx context.Context, id int) func(pct float64, rx, total int64) {
	var lastWrite time.Time
	return func(pct float64, rx, total int64) {
		if time.Since(lastWrite) < progressInterval {
			return
		}
		lastWrite = time.Now()
		_ = s.Store.SetProgress(ctx, id, pct/100, rx)
	}
}
