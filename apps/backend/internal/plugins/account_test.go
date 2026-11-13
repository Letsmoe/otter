package plugins

import (
	"context"
	"encoding/json"
	"strings"
	"testing"
)

// fakeSite plays a plugin whose site accepts one password and expires sessions.
type fakeSite struct {
	validSession string
	logins       int
	streamCalls  int
}

func (site *fakeSite) install(context.Context, string) error { return nil }

func (site *fakeSite) run(_ context.Context, _ *Plugin, stdin []byte, env []string) ([]byte, error) {
	var call struct {
		Capability string    `json:"capability"`
		Args       LoginArgs `json:"args"`
	}
	_ = json.Unmarshal(stdin, &call)
	if call.Capability == string(CapabilityLogin) {
		return site.login(call.Args)
	}
	site.streamCalls++
	if sessionOf(env) != site.validSession {
		return []byte(`{"error":"please log in","code":"logged_out"}`), nil
	}
	return []byte(`{"result":{"downloads":[{"label":"Proxer","url":"https://example/stream"}]}}`), nil
}

func (site *fakeSite) login(args LoginArgs) ([]byte, error) {
	site.logins++
	if args.Password != "right" {
		return []byte(`{"error":"Falsches Passwort","code":"login_failed"}`), nil
	}
	site.validSession = "session-" + strings.Repeat("x", site.logins)
	return json.Marshal(map[string]any{"result": LoginResult{Session: site.validSession}})
}

func sessionOf(env []string) string {
	for _, entry := range env {
		if strings.HasPrefix(entry, AccountSessionEnvKey+"=") {
			return strings.TrimPrefix(entry, AccountSessionEnvKey+"=")
		}
	}
	return ""
}

// memoryAccounts is the part of the accounts service the runner talks to.
type memoryAccounts struct {
	runner   *Runner
	session  string
	password string
	saved    bool
}

func (accounts *memoryAccounts) HasAccount(string) bool { return accounts.saved }

func (accounts *memoryAccounts) AccountEnv(string) []string {
	return []string{AccountSessionEnvKey + "=" + accounts.session}
}

func (accounts *memoryAccounts) Relogin(ctx context.Context, p *Plugin) error {
	result, err := accounts.runner.Login(ctx, p, LoginArgs{Username: "otter", Password: accounts.password})
	if err != nil {
		return err
	}
	accounts.session = result.Session
	return nil
}

func accountPlugin() *Plugin {
	return &Plugin{Meta: PluginMeta{
		Name:         "proxer",
		Capabilities: []Capability{CapabilityStreams, CapabilityLogin},
		Account:      &AccountRequirement{},
	}}
}

func TestAnExpiredSessionIsRenewedAndTheCallRetried(t *testing.T) {
	site := &fakeSite{validSession: "current"}
	runner := &Runner{executor: site}
	accounts := &memoryAccounts{runner: runner, session: "expired", password: "right", saved: true}
	runner.SetAccountProvider(accounts)

	result, err := runner.Streams(context.Background(), accountPlugin(), StreamsArgs{Title: "Frieren"})
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Downloads) != 1 || site.logins != 1 || site.streamCalls != 2 {
		t.Fatalf("downloads %d, logins %d, stream calls %d; want 1, 1, 2", len(result.Downloads), site.logins, site.streamCalls)
	}
}

func TestWithoutAnAccountThePluginIsNotCalled(t *testing.T) {
	site := &fakeSite{}
	runner := &Runner{executor: site}
	runner.SetAccountProvider(&memoryAccounts{runner: runner})

	_, err := runner.Streams(context.Background(), accountPlugin(), StreamsArgs{Title: "Frieren"})
	if !IsLoggedOut(err) || !IsExpectedFailure(err) {
		t.Fatalf("err = %v, want an expected logged-out failure", err)
	}
	if site.streamCalls != 0 {
		t.Fatal("the plugin ran without an account")
	}
}

func TestAWrongPasswordStopsAfterOneLogin(t *testing.T) {
	site := &fakeSite{validSession: "current"}
	runner := &Runner{executor: site}
	runner.SetAccountProvider(&memoryAccounts{runner: runner, session: "expired", password: "wrong", saved: true})

	_, err := runner.Streams(context.Background(), accountPlugin(), StreamsArgs{Title: "Frieren"})
	if err == nil || !strings.Contains(err.Error(), "Falsches Passwort") {
		t.Fatalf("err = %v, want the site's refusal", err)
	}
	if site.logins != 1 {
		t.Fatalf("logged in %d times, want 1", site.logins)
	}
}

func TestLoginArgumentsStayOutOfTraces(t *testing.T) {
	traced := traceableArgs(CapabilityLogin, LoginArgs{Username: "otter", Password: "hunter2"})
	encoded, _ := json.Marshal(traced)
	if strings.Contains(string(encoded), "hunter2") {
		t.Fatalf("traced %s", encoded)
	}
	if encoded, _ := json.Marshal(traceableArgs(CapabilityLogin, map[string]string{"password": "hunter2"})); strings.Contains(string(encoded), "hunter2") {
		t.Fatalf("traced %s", encoded)
	}
}
