package neoworks

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"

	libneoworks "github.com/neoworks-dev/libneoworks-go"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/secrets"
)

// A sign-in left open in the browser this long is forgotten.
const pendingSignInLifetime = 30 * time.Minute

// storeFileName is libneoworks' encrypted local store in DATA_DIR.
const storeFileName = "neoworks.db"

// ErrUnknownSignIn means a callback whose sign-in this backend didn't start,
// or started too long ago.
var ErrUnknownSignIn = errors.New("this sign-in was not started here or has expired; start it again")

// Status is what Settings shows about the Neoworks account.
type Status struct {
	SignedIn bool
	Email    string
	Name     string
}

// pendingSignIn is a sign-in waiting for its callback.
type pendingSignIn struct {
	verifier  string
	startedAt time.Time
}

// Service signs this backend in to Neoworks and holds its libneoworks client.
type Service struct {
	database    *db.DB
	box         *secrets.Box
	keystore    keystore
	storePath   string
	installName string
	endpoints   Endpoints
	httpClient  *http.Client
	// acceptGrant hands the grant to libneoworks; tests replace it, since a
	// real grant needs a real account's signature.
	acceptGrant func(client *libneoworks.Client, grant, ownerIdentity json.RawMessage) error
	// afterSignIn starts the first sync; tests that only sign in skip it.
	afterSignIn func(ctx context.Context)

	mutex   sync.Mutex
	pending map[string]pendingSignIn
	install *libneoworks.Install
	client  *libneoworks.Client
	// descriptorReady: the registry confirmed @letsmoe/otter for this client.
	descriptorReady bool
	lastSync        *SyncResult

	// syncMutex lets one sync run at a time.
	syncMutex sync.Mutex
}

// NewService keeps libneoworks' store in dataDir. installName is how the
// account lists this install, e.g. "Otter on Pixel 8".
func NewService(database *db.DB, box *secrets.Box, dataDir, installName string, endpoints Endpoints) *Service {
	service := &Service{
		database:    database,
		box:         box,
		keystore:    newKeystore(box),
		storePath:   filepath.Join(dataDir, storeFileName),
		installName: installName,
		endpoints:   endpoints,
		httpClient:  &http.Client{Timeout: 20 * time.Second},
		acceptGrant: func(client *libneoworks.Client, grant, ownerIdentity json.RawMessage) error {
			return client.AcceptGrant(grant, ownerIdentity)
		},
		pending: map[string]pendingSignIn{},
	}
	service.afterSignIn = func(ctx context.Context) { go service.syncInBackground(ctx) }
	return service
}

func (s *Service) Status(ctx context.Context) (Status, error) {
	row, err := loadAccountRow(ctx, s.database)
	if err != nil {
		return Status{}, err
	}
	return Status{SignedIn: row.signedIn(), Email: row.Email, Name: row.Name}, nil
}

// StartSignIn returns the Neoworks page to open. returnOrigin is the web app's
// origin (e.g. http://nas.local:3000), empty for the Android app.
func (s *Service) StartSignIn(ctx context.Context, returnOrigin string) (string, error) {
	install, err := s.loadInstall(ctx)
	if err != nil {
		return "", err
	}
	description, err := install.Describe()
	if err != nil {
		return "", fmt.Errorf("neoworks install: %w", err)
	}
	verifier, err := randomToken()
	if err != nil {
		return "", err
	}
	nonce, err := randomToken()
	if err != nil {
		return "", err
	}
	state := signInState(returnOrigin, nonce)
	s.rememberSignIn(state, verifier)
	return authorizeURL(s.endpoints, description, s.installName, state, verifier), nil
}

// CompleteSignIn takes the callback's code: it exchanges it, hands the grant to
// libneoworks and saves the session.
func (s *Service) CompleteSignIn(ctx context.Context, code, state string) (Status, error) {
	verifier, found := s.takeSignIn(state)
	if !found {
		return Status{}, ErrUnknownSignIn
	}
	answer, err := exchangeCode(ctx, s.httpClient, s.endpoints, code, verifier)
	if err != nil {
		return Status{}, err
	}
	if len(answer.Grant) == 0 {
		return Status{}, errors.New("neoworks granted no access to Otter's data; allow it on the consent page")
	}
	userID, err := subjectOf(answer.AccessToken)
	if err != nil {
		return Status{}, err
	}
	if err := s.acceptOwnerGrant(ctx, answer, userID); err != nil {
		return Status{}, err
	}
	status, err := s.saveSignIn(ctx, answer, userID)
	if err != nil {
		return Status{}, err
	}
	// The first sync pulls the account's roots and adopts what this device had.
	s.afterSignIn(context.WithoutCancel(ctx))
	return status, nil
}

func (s *Service) acceptOwnerGrant(ctx context.Context, answer tokens, userID string) error {
	identity, err := fetchOwnerIdentity(ctx, s.httpClient, s.endpoints, answer.AccessToken, userID)
	if err != nil {
		return err
	}
	client, err := s.openClient(ctx)
	if err != nil {
		return err
	}
	if err := s.acceptGrant(client, answer.Grant, identity); err != nil {
		return fmt.Errorf("neoworks grant: %w", err)
	}
	return nil
}

func (s *Service) saveSignIn(ctx context.Context, answer tokens, userID string) (Status, error) {
	info, err := fetchUserInfo(ctx, s.httpClient, s.endpoints, answer.AccessToken)
	if err != nil {
		// The account works without a display name; Settings shows the user id instead.
		log.Printf("neoworks: user info: %v", err)
	}
	row, err := s.sealedSession(answer, userID, info)
	if err != nil {
		return Status{}, err
	}
	if err := saveSession(ctx, s.database, row); err != nil {
		return Status{}, err
	}
	return Status{SignedIn: true, Email: info.Email, Name: info.Name}, nil
}

func (s *Service) sealedSession(answer tokens, userID string, info userInfo) (accountRow, error) {
	sealedAccess, err := s.box.Seal(answer.AccessToken)
	if err != nil {
		return accountRow{}, err
	}
	sealedRefresh, err := s.box.Seal(answer.RefreshToken)
	if err != nil {
		return accountRow{}, err
	}
	now := time.Now()
	return accountRow{
		UserID: userID, Email: info.Email, Name: info.Name,
		AccessToken: sealedAccess, RefreshToken: sealedRefresh,
		AccessExpiresAt: db.FormatTime(expiresAt(answer, now)), SignedInAt: db.FormatTime(now),
	}, nil
}

// ErrSignedOut means no Neoworks account is signed in on this backend.
var ErrSignedOut = errors.New("not signed in to Neoworks")

// AccessToken returns a working access token, refreshing it when it expired.
func (s *Service) AccessToken(ctx context.Context) (string, error) {
	row, err := loadAccountRow(ctx, s.database)
	if err != nil {
		return "", err
	}
	if !row.signedIn() {
		return "", ErrSignedOut
	}
	expires, err := time.Parse(db.TimeFormat, row.AccessExpiresAt)
	if err == nil && time.Now().Before(expires) {
		return s.box.Open(row.AccessToken)
	}
	return s.refreshAccessToken(ctx, row)
}

func (s *Service) refreshAccessToken(ctx context.Context, row accountRow) (string, error) {
	refreshToken, err := s.box.Open(row.RefreshToken)
	if err != nil {
		return "", err
	}
	answer, err := refreshTokens(ctx, s.httpClient, s.endpoints, refreshToken)
	if err != nil {
		return "", err
	}
	// Servers that don't rotate refresh tokens answer without a new one.
	if answer.RefreshToken == "" {
		answer.RefreshToken = refreshToken
	}
	sealedAccess, err := s.box.Seal(answer.AccessToken)
	if err != nil {
		return "", err
	}
	sealedRefresh, err := s.box.Seal(answer.RefreshToken)
	if err != nil {
		return "", err
	}
	if err := saveAccessToken(ctx, s.database, sealedAccess, sealedRefresh, expiresAt(answer, time.Now())); err != nil {
		return "", err
	}
	return answer.AccessToken, nil
}

// SignOut forgets the account, the install and the local store on this device;
// the household's data stays in Neoworks for its other devices.
func (s *Service) SignOut(ctx context.Context) error {
	s.syncMutex.Lock()
	defer s.syncMutex.Unlock()
	s.mutex.Lock()
	defer s.mutex.Unlock()
	s.closeLocked()
	s.lastSync = nil
	if err := forgetAccount(ctx, s.database); err != nil {
		return err
	}
	if err := forgetMirroredRows(ctx, s.database); err != nil {
		return err
	}
	for _, suffix := range []string{"", "-wal", "-shm"} {
		if err := os.Remove(s.storePath + suffix); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
	}
	return nil
}

// Close frees the libneoworks handles once a running sync finished.
func (s *Service) Close() {
	s.syncMutex.Lock()
	defer s.syncMutex.Unlock()
	s.mutex.Lock()
	defer s.mutex.Unlock()
	s.closeLocked()
}

func (s *Service) closeLocked() {
	s.descriptorReady = false
	if s.client != nil {
		s.client.Close()
		s.client = nil
	}
	if s.install != nil {
		s.install.Close()
		s.install = nil
	}
}

// loadInstall opens the saved install identity, creating and saving one the first time.
func (s *Service) loadInstall(ctx context.Context) (*libneoworks.Install, error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	if s.install != nil {
		return s.install, nil
	}
	row, err := loadAccountRow(ctx, s.database)
	if err != nil {
		return nil, err
	}
	if row.Install == "" {
		return s.createInstallLocked(ctx)
	}
	sealed, err := base64.StdEncoding.DecodeString(row.Install)
	if err != nil {
		return nil, fmt.Errorf("neoworks install: %w", err)
	}
	install, err := libneoworks.OpenInstall(sealed, s.keystore)
	if err != nil {
		return nil, fmt.Errorf("neoworks install: %w", err)
	}
	s.install = install
	return install, nil
}

func (s *Service) createInstallLocked(ctx context.Context) (*libneoworks.Install, error) {
	install, err := libneoworks.NewInstall()
	if err != nil {
		return nil, fmt.Errorf("neoworks install: %w", err)
	}
	sealed, err := install.Seal(s.keystore)
	if err != nil {
		install.Close()
		return nil, fmt.Errorf("neoworks install: %w", err)
	}
	if err := saveInstall(ctx, s.database, sealed); err != nil {
		install.Close()
		return nil, err
	}
	s.install = install
	return install, nil
}

// openClient opens libneoworks' client over the local store.
func (s *Service) openClient(ctx context.Context) (*libneoworks.Client, error) {
	install, err := s.loadInstall(ctx)
	if err != nil {
		return nil, err
	}
	s.mutex.Lock()
	defer s.mutex.Unlock()
	if s.client != nil {
		return s.client, nil
	}
	client, err := libneoworks.OpenClient(install, s.storePath)
	if err != nil {
		return nil, fmt.Errorf("neoworks store: %w", err)
	}
	s.client = client
	return client, nil
}

func (s *Service) rememberSignIn(state, verifier string) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	for oldState, pending := range s.pending {
		if time.Since(pending.startedAt) > pendingSignInLifetime {
			delete(s.pending, oldState)
		}
	}
	s.pending[state] = pendingSignIn{verifier: verifier, startedAt: time.Now()}
}

// takeSignIn returns the verifier of a pending sign-in once.
func (s *Service) takeSignIn(state string) (string, bool) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	pending, found := s.pending[state]
	delete(s.pending, state)
	if !found || time.Since(pending.startedAt) > pendingSignInLifetime {
		return "", false
	}
	return pending.verifier, true
}
