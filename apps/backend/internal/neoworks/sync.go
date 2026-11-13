package neoworks

import (
	"context"
	"errors"
	"fmt"
	"log"
	"time"

	libneoworks "github.com/neoworks-dev/libneoworks-go"
)

// syncInterval is how often a signed-in backend syncs on its own.
const syncInterval = 10 * time.Minute

// SyncResult is what the last sync did, for Settings.
type SyncResult struct {
	At time.Time
	// Received counts local records changed by other devices, Sent rows written from here.
	Received int
	Sent     int
	// Failure is why the run stopped; empty when it worked.
	Failure string
}

// Start syncs now and then every syncInterval while signed in, until ctx ends.
func (s *Service) Start(ctx context.Context) {
	go func() {
		ticker := time.NewTicker(syncInterval)
		defer ticker.Stop()
		for {
			s.syncInBackground(ctx)
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
			}
		}
	}()
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
	mirror := profileMirror{database: s.database, store: store}
	received, err := mirror.pull(ctx)
	if err != nil {
		return SyncResult{Received: received}, fmt.Errorf("apply profiles: %w", err)
	}
	sent, err := mirror.push(ctx)
	if err != nil {
		return SyncResult{Received: received, Sent: sent}, fmt.Errorf("write profiles: %w", err)
	}
	result := SyncResult{Received: received, Sent: sent}
	if sent == 0 {
		return result, nil
	}
	return result, s.exchange(ctx, client)
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
