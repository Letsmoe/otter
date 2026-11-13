package neoworks

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"

	libneoworks "github.com/neoworks-dev/libneoworks-go"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/secrets"
)

// fakeNeoworks answers the OAuth and API requests of a sign-in like neoworks.dev.
type fakeNeoworks struct {
	server *httptest.Server

	mutex sync.Mutex
	// challenge is the PKCE challenge the authorize URL carried.
	challenge string
	// accessToken is what the token endpoint hands out next; expiresIn its lifetime.
	accessToken string
	expiresIn   int
	refreshes   int
	withGrant   bool
}

func newFakeNeoworks(t *testing.T) *fakeNeoworks {
	t.Helper()
	fake := &fakeNeoworks{accessToken: testJWT("user-42", "first"), expiresIn: 3600, withGrant: true}
	mux := http.NewServeMux()
	mux.HandleFunc("POST /oauth/token", fake.token)
	mux.HandleFunc("GET /oauth/userinfo", fake.userInfo)
	mux.HandleFunc("GET /api/v1/keys/identity", fake.identity)
	fake.server = httptest.NewServer(mux)
	t.Cleanup(fake.server.Close)
	return fake
}

func (fake *fakeNeoworks) endpoints() Endpoints {
	return Endpoints{OAuth: fake.server.URL, API: fake.server.URL + "/api/v1"}
}

func testJWT(subject, marker string) string {
	payload, _ := json.Marshal(map[string]string{"sub": subject, "jti": marker})
	return "header." + base64.RawURLEncoding.EncodeToString(payload) + ".signature"
}

func (fake *fakeNeoworks) token(writer http.ResponseWriter, request *http.Request) {
	fake.mutex.Lock()
	defer fake.mutex.Unlock()
	if request.FormValue("client_id") != clientID {
		http.Error(writer, "unknown client", http.StatusUnauthorized)
		return
	}
	answer := map[string]any{"access_token": fake.accessToken, "refresh_token": "refresh-1", "expires_in": fake.expiresIn}
	if request.FormValue("grant_type") == "refresh_token" {
		fake.refreshes++
		answer["access_token"] = testJWT("user-42", "refreshed")
		writeJSON(writer, answer)
		return
	}
	if codeChallenge(request.FormValue("code_verifier")) != fake.challenge {
		http.Error(writer, "PKCE verifier does not match", http.StatusBadRequest)
		return
	}
	if fake.withGrant {
		answer["neoworks_grant"] = map[string]any{"installId": "install", "grants": []any{}}
	}
	writeJSON(writer, answer)
}

func (fake *fakeNeoworks) userInfo(writer http.ResponseWriter, request *http.Request) {
	writeJSON(writer, map[string]string{"sub": "user-42", "email": "moritz@example.com", "name": "Moritz"})
}

func (fake *fakeNeoworks) identity(writer http.ResponseWriter, request *http.Request) {
	if !strings.HasPrefix(request.Header.Get("Authorization"), "Bearer ") || request.URL.Query().Get("userId") != "user-42" {
		http.Error(writer, "forbidden", http.StatusForbidden)
		return
	}
	writeJSON(writer, map[string]string{"userId": "user-42", "signPub": "owner-key"})
}

func writeJSON(writer http.ResponseWriter, value any) {
	writer.Header().Set("Content-Type", "application/json")
	json.NewEncoder(writer).Encode(value)
}

type acceptedGrant struct {
	grant    json.RawMessage
	identity json.RawMessage
}

func newTestService(t *testing.T, fake *fakeNeoworks) (*Service, *[]acceptedGrant) {
	t.Helper()
	dataDir := t.TempDir()
	database, err := db.Open(context.Background(), filepath.Join(dataDir, "otter.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	box, err := secrets.New(bytes.Repeat([]byte{9}, 32))
	if err != nil {
		t.Fatal(err)
	}
	service := NewService(database, box, dataDir, "Otter on a test", fake.endpoints())
	t.Cleanup(service.Close)
	accepted := []acceptedGrant{}
	service.acceptGrant = func(_ *libneoworks.Client, grant, identity json.RawMessage) error {
		accepted = append(accepted, acceptedGrant{grant: grant, identity: identity})
		return nil
	}
	return service, &accepted
}

// startSignIn opens the sign-in like the app does and returns its state.
func startSignIn(t *testing.T, service *Service, fake *fakeNeoworks, returnOrigin string) url.Values {
	t.Helper()
	address, err := service.StartSignIn(context.Background(), returnOrigin)
	if err != nil {
		t.Fatal(err)
	}
	parsed, err := url.Parse(address)
	if err != nil {
		t.Fatal(err)
	}
	query := parsed.Query()
	fake.mutex.Lock()
	fake.challenge = query.Get("code_challenge")
	fake.mutex.Unlock()
	return query
}

func TestTheSignInPageAsksForOttersDataForThisInstall(t *testing.T) {
	fake := newFakeNeoworks(t)
	service, _ := newTestService(t, fake)
	query := startSignIn(t, service, fake, "")

	if query.Get("client_id") != clientID || query.Get("redirect_uri") != "https://otter.letsmoe.com/auth/callback" {
		t.Fatalf("client %q, redirect %q", query.Get("client_id"), query.Get("redirect_uri"))
	}
	if !strings.Contains(query.Get("scope"), "@letsmoe/otter:write") || query.Get("code_challenge_method") != "S256" {
		t.Fatalf("scope %q, method %q", query.Get("scope"), query.Get("code_challenge_method"))
	}
	if query.Get("install_id") == "" || query.Get("install_sign_pub") == "" || query.Get("install_name") != "Otter on a test" {
		t.Fatalf("install parameters missing: %v", query)
	}
	if !strings.HasPrefix(query.Get("state"), "app.") {
		t.Fatalf("state %q, want the app's", query.Get("state"))
	}
}

func TestAWebSignInCarriesItsOriginForTheRelay(t *testing.T) {
	fake := newFakeNeoworks(t)
	service, _ := newTestService(t, fake)
	state := startSignIn(t, service, fake, "http://nas.local:3000").Get("state")
	parts := strings.Split(state, ".")
	origin, _ := base64.RawURLEncoding.DecodeString(parts[1])
	if parts[0] != "web" || string(origin) != "http://nas.local:3000" || len(parts) != 3 {
		t.Fatalf("state %q", state)
	}
}

func TestTheInstallStaysTheSameUntilSignOut(t *testing.T) {
	fake := newFakeNeoworks(t)
	service, _ := newTestService(t, fake)
	first := startSignIn(t, service, fake, "").Get("install_id")
	service.Close()
	second := startSignIn(t, service, fake, "").Get("install_id")
	if first != second {
		t.Fatalf("install changed from %q to %q after reopening", first, second)
	}
}

func TestACompletedSignInAcceptsTheGrantAndSavesTheSession(t *testing.T) {
	fake := newFakeNeoworks(t)
	service, accepted := newTestService(t, fake)
	ctx := context.Background()
	state := startSignIn(t, service, fake, "").Get("state")

	status, err := service.CompleteSignIn(ctx, "code-1", state)
	if err != nil {
		t.Fatal(err)
	}
	if !status.SignedIn || status.Email != "moritz@example.com" {
		t.Fatalf("status = %+v", status)
	}
	if len(*accepted) != 1 || !strings.Contains(string((*accepted)[0].identity), "owner-key") {
		t.Fatalf("grant handed to libneoworks: %+v", *accepted)
	}
	token, err := service.AccessToken(ctx)
	if err != nil || token != fake.accessToken {
		t.Fatalf("AccessToken = %q, %v", token, err)
	}
	stored, _ := service.Status(ctx)
	if !stored.SignedIn || stored.Name != "Moritz" {
		t.Fatalf("stored status = %+v", stored)
	}
}

func TestACallbackIsAcceptedOnlyOnce(t *testing.T) {
	fake := newFakeNeoworks(t)
	service, _ := newTestService(t, fake)
	state := startSignIn(t, service, fake, "").Get("state")
	if _, err := service.CompleteSignIn(context.Background(), "code-1", state); err != nil {
		t.Fatal(err)
	}
	if _, err := service.CompleteSignIn(context.Background(), "code-1", state); !errors.Is(err, ErrUnknownSignIn) {
		t.Fatalf("second callback: %v, want ErrUnknownSignIn", err)
	}
	if _, err := service.CompleteSignIn(context.Background(), "code-2", "app.made-up"); !errors.Is(err, ErrUnknownSignIn) {
		t.Fatalf("made-up state: %v", err)
	}
}

func TestDeclinedAccessIsNoSignIn(t *testing.T) {
	fake := newFakeNeoworks(t)
	fake.withGrant = false
	service, _ := newTestService(t, fake)
	state := startSignIn(t, service, fake, "").Get("state")
	if _, err := service.CompleteSignIn(context.Background(), "code-1", state); err == nil {
		t.Fatal("a token without a grant signed in")
	}
	if status, _ := service.Status(context.Background()); status.SignedIn {
		t.Fatal("signed in without a grant")
	}
}

func TestAnExpiredAccessTokenIsRefreshed(t *testing.T) {
	fake := newFakeNeoworks(t)
	fake.expiresIn = 30 // shorter than the minute kept in reserve: expired at once
	service, _ := newTestService(t, fake)
	ctx := context.Background()
	state := startSignIn(t, service, fake, "").Get("state")
	if _, err := service.CompleteSignIn(ctx, "code-1", state); err != nil {
		t.Fatal(err)
	}
	token, err := service.AccessToken(ctx)
	if err != nil || token != testJWT("user-42", "refreshed") || fake.refreshes != 1 {
		t.Fatalf("AccessToken = %q, %v after %d refreshes", token, err, fake.refreshes)
	}
}

func TestSignOutForgetsTheAccountAndTheLocalStore(t *testing.T) {
	fake := newFakeNeoworks(t)
	service, _ := newTestService(t, fake)
	ctx := context.Background()
	state := startSignIn(t, service, fake, "")
	if _, err := service.CompleteSignIn(ctx, "code-1", state.Get("state")); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(service.storePath); err != nil {
		t.Fatalf("no local store after sign-in: %v", err)
	}
	if err := service.SignOut(ctx); err != nil {
		t.Fatal(err)
	}
	if status, _ := service.Status(ctx); status.SignedIn {
		t.Fatal("still signed in")
	}
	if _, err := service.AccessToken(ctx); !errors.Is(err, ErrSignedOut) {
		t.Fatalf("AccessToken after sign-out: %v", err)
	}
	if _, err := os.Stat(service.storePath); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("local store left behind: %v", err)
	}
	if next := startSignIn(t, service, fake, "").Get("install_id"); next == state.Get("install_id") {
		t.Fatal("the next sign-in reused the old install")
	}
}
