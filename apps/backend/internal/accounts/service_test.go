package accounts

import (
	"context"
	"errors"
	"path/filepath"
	"strings"
	"testing"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/plugins"
	"github.com/Letsmoe/otter/internal/secrets"
)

type fakeLogin struct {
	calls int
}

func (login *fakeLogin) Login(_ context.Context, _ *plugins.Plugin, args plugins.LoginArgs) (*plugins.LoginResult, error) {
	login.calls++
	if args.Password != "hunter2" {
		return nil, &plugins.PluginError{Plugin: "proxer", Message: "Falsches Passwort", Code: plugins.ErrorCodeLoginFailed}
	}
	return &plugins.LoginResult{Session: "cookie=abc"}, nil
}

var proxer = &plugins.Plugin{Meta: plugins.PluginMeta{Name: "proxer", Account: &plugins.AccountRequirement{}}}

func newTestService(t *testing.T) (*Service, *db.DB, *fakeLogin) {
	database, err := db.Open(context.Background(), filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { database.Close() })
	box, _ := secrets.New([]byte(strings.Repeat("k", 32)))
	login := &fakeLogin{}
	service, err := NewService(context.Background(), database, box, login)
	if err != nil {
		t.Fatal(err)
	}
	return service, database, login
}

func TestALoginIsStoredSealed(t *testing.T) {
	ctx := context.Background()
	service, database, _ := newTestService(t)
	if err := service.Login(ctx, proxer, "otter", "hunter2"); err != nil {
		t.Fatal(err)
	}
	row, _ := database.QueryRow(ctx, `SELECT * FROM plugin_account WHERE plugin = 'proxer'`, nil)
	for column, value := range row {
		text, _ := value.(string)
		if strings.Contains(text, "hunter2") || strings.Contains(text, "cookie=abc") {
			t.Fatalf("column %s holds a secret in the clear", column)
		}
	}
	status := service.Status("proxer")
	if status == nil || !status.LoggedIn || status.Username != "otter" {
		t.Fatalf("status = %+v", status)
	}
	env := strings.Join(service.AccountEnv("proxer"), "\n")
	if !strings.Contains(env, plugins.AccountSessionEnvKey+"=cookie=abc") {
		t.Fatalf("env = %q, want the session", env)
	}

	restarted, err := NewService(ctx, database, service.box, service.runner)
	if err != nil || !restarted.HasAccount("proxer") {
		t.Fatal("the account didn't survive a restart")
	}
}

func TestARefusedLoginIsNotSaved(t *testing.T) {
	service, _, _ := newTestService(t)
	err := service.Login(context.Background(), proxer, "otter", "wrong")
	var pluginError *plugins.PluginError
	if !errors.As(err, &pluginError) || pluginError.Message != "Falsches Passwort" {
		t.Fatalf("err = %v, want the site's refusal", err)
	}
	if service.HasAccount("proxer") {
		t.Fatal("refused credentials were saved")
	}
}

func TestParallelExpiredCallsLogInOnce(t *testing.T) {
	ctx := context.Background()
	service, _, login := newTestService(t)
	_ = service.Login(ctx, proxer, "otter", "hunter2")
	for call := 0; call < 3; call++ {
		if err := service.Relogin(ctx, proxer); err != nil {
			t.Fatal(err)
		}
	}
	if login.calls != 2 {
		t.Fatalf("logged in %d times, want the first login and one renewal", login.calls)
	}
}

func TestLogoutForgetsTheAccount(t *testing.T) {
	ctx := context.Background()
	service, database, _ := newTestService(t)
	_ = service.Login(ctx, proxer, "otter", "hunter2")
	if err := service.Logout(ctx, "proxer"); err != nil {
		t.Fatal(err)
	}
	count, _ := database.Int(ctx, `SELECT COUNT(*) FROM plugin_account`, nil)
	if service.HasAccount("proxer") || count != 0 {
		t.Fatal("the account is still there")
	}
}
