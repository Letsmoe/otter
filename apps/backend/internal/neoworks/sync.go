package neoworks

import (
	"context"
	"errors"
	"fmt"
	"log"
	"time"

	libneoworks "github.com/neoworks-dev/libneoworks-go"
)

const (
	// syncInterval is how often a signed-in backend syncs on its own.
	syncInterval = 10 * time.Minute
	// After a local change, sync once changes paused this long, but no later
	// than changeMaxWait after the first unsynced one (progress saves keep coming while watching).
	changeQuiet   = 5 * time.Second
	changeMaxWait = time.Minute
)

// SyncResult is what the last sync did, for Settings.
type SyncResult struct {
	At time.Time
	// Received counts local records changed by other devices, Sent rows written from here.
	Received int
	Sent     int
	// Failure is why the run stopped; empty when it worked.
	Failure string
}

// Start syncs now, every syncInterval, after local changes (NotifyChange) and
// when an app opens (SyncSoon), until ctx ends.
func (s *Service) Start(ctx context.Context) {
	go s.syncLoop(ctx)
}

// NotifyChange tells sync that synced data changed here; it syncs shortly.
func (s *Service) NotifyChange() {
	select {
	case s.changes <- struct{}{}:
	default:
	}
}

// SyncSoon syncs right away in the background, e.g. when an app was opened.
func (s *Service) SyncSoon() {
	select {
	case s.wakeups <- struct{}{}:
	default:
	}
}

func (s *Service) syncLoop(ctx context.Context) {
	s.syncInBackground(ctx)
	ticker := time.NewTicker(syncInterval)
	defer ticker.Stop()
	pending := changeTimers{}
	for {
		select {
		case <-ctx.Done():
			return
		case <-s.changes:
			pending.noteChange()
			continue
		case <-ticker.C:
		case <-s.wakeups:
		case <-pending.quiet:
		case <-pending.deadline:
		}
		pending = changeTimers{}
		s.syncInBackground(ctx)
	}
}

// changeTimers wait for local changes to pause; nil channels never fire.
type changeTimers struct {
	quiet    <-chan time.Time
	deadline <-chan time.Time
}

func (timers *changeTimers) noteChange() {
	timers.quiet = time.After(changeQuiet)
	if timers.deadline == nil {
		timers.deadline = time.After(changeMaxWait)
	}
}

func (s *Service) syncInBackground(ctx context.Context) {
	if _, err := s.Sync(ctx); err != nil && !errors.Is(err, ErrSignedOut) {
		log.Printf("neoworks: sync: %v", err)
	}
}

// LastSync is the result of the last sync run, nil before the first.
func (s *Service) LastSync() *SyncResult {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	return s.lastSync
}

// Sync exchanges changes with Neoworks: pulls what other devices changed into
// Otter's tables, writes what changed here and pushes it.
func (s *Service) Sync(ctx context.Context) (SyncResult, error) {
	s.syncMutex.Lock()
	defer s.syncMutex.Unlock()
	status, err := s.Status(ctx)
	if err != nil {
		return SyncResult{}, err
	}
	if !status.SignedIn {
		return SyncResult{}, ErrSignedOut
	}
	result, err := s.runSync(ctx)
	result.At = time.Now()
	if err != nil {
		result.Failure = err.Error()
	}
	s.mutex.Lock()
	s.lastSync = &result
	s.mutex.Unlock()
	return result, err
}

func (s *Service) runSync(ctx context.Context) (SyncResult, error) {
	client, err := s.openClient(ctx)
	if err != nil {
		return SyncResult{}, err
	}
	if err := s.ensureDescriptor(ctx, client); err != nil {
		return SyncResult{}, err
	}
	if err := s.exchange(ctx, client); err != nil {
		return SyncResult{}, err
	}
	s.adoptLocalRows(client)
	store := clientRows{client: client}
	if err := ensureRoot(store, client); err != nil {
		return SyncResult{}, err
	}
	result, err := s.mirrorAll(ctx, store)
	if err != nil || result.Sent == 0 {
		return result, err
	}
	return result, s.exchange(ctx, client)
}

// mirrorAll applies what other devices changed, then writes what changed here.
// Profiles go first both ways: history, lists and ratings belong to their rows.
func (s *Service) mirrorAll(ctx context.Context, store rowStore) (SyncResult, error) {
	result := SyncResult{}
	profiles := profileMirror{database: s.database, store: store}
	received, err := profiles.pull(ctx)
	result.Received += received
	if err != nil {
		return result, fmt.Errorf("apply profiles: %w", err)
	}
	received, err = s.pullItems(ctx, store)
	result.Received += received
	if err != nil {
		return result, err
	}
	sent, err := profiles.push(ctx)
	result.Sent += sent
	if err != nil {
		return result, fmt.Errorf("write profiles: %w", err)
	}
	sent, err = s.pushItems(ctx, store)
	result.Sent += sent
	if err != nil || s.householdSettings == nil {
		return result, err
	}
	mirror := settingsMirror{database: s.database, store: store, settings: s.householdSettings, restoreDisabled: s.restoreDisabled}
	received, sent, err = mirror.sync(ctx)
	result.Received += received
	result.Sent += sent
	if err != nil {
		return result, fmt.Errorf("settings: %w", err)
	}
	return result, nil
}

// SetSettings lets sync read and change the household's settings;
// restoreDisabled switches plugins off in the running plugin registry.
func (s *Service) SetSettings(household HouseholdSettings, restoreDisabled func(names []string)) {
	s.householdSettings = household
	s.restoreDisabled = restoreDisabled
}

// pullItems applies the watch progress, list entries and ratings other devices changed.
func (s *Service) pullItems(ctx context.Context, store rowStore) (int, error) {
	profileLinks, err := loadProfileRows(ctx, s.database)
	if err != nil {
		return 0, err
	}
	received := 0
	for _, spec := range itemSpecs {
		count, err := itemMirror{database: s.database, store: store, spec: spec}.pull(ctx, profileLinks)
		received += count
		if err != nil {
			return received, fmt.Errorf("apply %s: %w", spec.table, err)
		}
	}
	return received, nil
}

// pushItems writes the watch progress, list entries and ratings changed here.
func (s *Service) pushItems(ctx context.Context, store rowStore) (int, error) {
	profileLinks, err := loadProfileRows(ctx, s.database)
	if err != nil {
		return 0, err
	}
	sent := 0
	for _, spec := range itemSpecs {
		count, err := itemMirror{database: s.database, store: store, spec: spec}.push(ctx, profileLinks)
		sent += count
		if err != nil {
			return sent, fmt.Errorf("write %s: %w", spec.table, err)
		}
	}
	return sent, nil
}

// ensureDescriptor registers @letsmoe/otter with the client and has the
// registry confirm the descriptor's bytes, once per client.
func (s *Service) ensureDescriptor(ctx context.Context, client *libneoworks.Client) error {
	s.mutex.Lock()
	ready := s.descriptorReady
	s.mutex.Unlock()
	if ready {
		return nil
	}
	check, err := client.RegisterDescriptor(collection, descriptor, schemaVersion)
	if err != nil {
		return fmt.Errorf("register %s: %w", collection, err)
	}
	if check != nil {
		answer, err := transport{service: s}.Do(ctx, *check)
		if err != nil {
			return fmt.Errorf("confirm %s: %w", collection, err)
		}
		if err := client.CompleteDescriptorCheck(collection, answer.Status, answer.Body); err != nil {
			return fmt.Errorf("confirm %s (HTTP %d): %w", collection, answer.Status, err)
		}
	}
	s.mutex.Lock()
	s.descriptorReady = true
	s.mutex.Unlock()
	return nil
}

// exchange runs one libneoworks sync: push, then pull.
func (s *Service) exchange(ctx context.Context, client *libneoworks.Client) error {
	report, err := client.Sync(ctx, transport{service: s})
	if err != nil {
		return fmt.Errorf("sync: %w", err)
	}
	if len(report.IntegrityFailures) > 0 {
		log.Printf("neoworks: access log of %d rows failed verification: %v", len(report.IntegrityFailures), report.IntegrityFailures)
	}
	if report.FailureCode != 0 {
		return fmt.Errorf("sync: %s", report.Failure)
	}
	return nil
}

// ensureRoot creates the @letsmoe/otter root when the pull brought none: rows
// hang under it. Neoworks may keep that to the account itself, so a refusal is
// reported as such.
func ensureRoot(store clientRows, client *libneoworks.Client) error {
	present, err := store.hasRoot()
	if err != nil || present {
		return err
	}
	if _, err := client.CreateRoot(collection); err != nil {
		return fmt.Errorf("the Neoworks account has no place for Otter's data yet, and this device may not create one: %w", err)
	}
	return nil
}

// adoptLocalRows moves rows written before sign-in into the account. A
// collection without an account root yet keeps them until a later sync.
func (s *Service) adoptLocalRows(client *libneoworks.Client) {
	report, err := client.AdoptLocalRows()
	if err != nil {
		var status libneoworks.Status
		if !errors.As(err, &status) || status != libneoworks.StatusInvalidState {
			log.Printf("neoworks: adopting local rows: %v", err)
		}
		return
	}
	if len(report.PendingCollections) > 0 {
		log.Printf("neoworks: %v have no account root yet; their rows stay local", report.PendingCollections)
	}
}
