// Package accounts keeps the user's logins for plugins whose site needs an
// account (e.g. proxer.me). The password stays sealed (internal/secrets) so
// the backend can log in again when a session expires; it is never returned
// by the API, and plugins only ever get the session.
package accounts

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/Letsmoe/otter/internal/db"
	"github.com/Letsmoe/otter/internal/plugins"
	"github.com/Letsmoe/otter/internal/secrets"
)

// Parallel calls that all find the session expired log in again only once.
const reloginCooldown = 30 * time.Second

// account is a plugin_account row; Password and Session are sealed.
type account struct {
	Plugin           string `json:"plugin"`
	Username         string `json:"username"`
	Password         string `json:"password"`
	Session          string `json:"session"`
	SessionExpiresAt string `json:"session_expires_at"`
	Error            string `json:"error"`
	UpdatedAt        string `json:"updated_at"`
}

// Status is what the apps see of an account.
type Status struct {
	Username string
	LoggedIn bool
	// Error is why the last login failed; empty when it worked.
	Error string
}

type loginRunner interface {
	Login(ctx context.Context, p *plugins.Plugin, args plugins.LoginArgs) (*plugins.LoginResult, error)
}

type Service struct {
	database *db.DB
	box      *secrets.Box
	runner   loginRunner

	mutex    sync.RWMutex
	accounts map[string]account
	// renewedAt is when each plugin last logged in again.
	renewedAt    map[string]time.Time
	reloginMutex sync.Mutex
}

// NewService loads the saved accounts.
func NewService(ctx context.Context, database *db.DB, box *secrets.Box, runner loginRunner) (*Service, error) {
	rows, err := db.QueryAs[account](ctx, database, `SELECT * FROM plugin_account`, nil)
	if err != nil {
		return nil, fmt.Errorf("load plugin accounts: %w", err)
	}
	service := &Service{database: database, box: box, runner: runner, accounts: map[string]account{}, renewedAt: map[string]time.Time{}}
	for _, row := range rows {
		service.accounts[row.Plugin] = row
	}
	return service, nil
}

func (s *Service) lookup(pluginName string) (account, bool) {
	s.mutex.RLock()
	defer s.mutex.RUnlock()
	found, ok := s.accounts[pluginName]
	return found, ok
}

func (s *Service) HasAccount(pluginName string) bool {
	_, found := s.lookup(pluginName)
	return found
}

// Status is nil when no account is saved for the plugin.
func (s *Service) Status(pluginName string) *Status {
	found, ok := s.lookup(pluginName)
	if !ok {
		return nil
	}
	return &Status{Username: found.Username, LoggedIn: found.Session != "" && found.Error == "", Error: found.Error}
}

// AccountEnv gives a plugin its username and session.
func (s *Service) AccountEnv(pluginName string) []string {
	found, ok := s.lookup(pluginName)
	if !ok {
		return nil
	}
	session, err := s.box.Open(found.Session)
	if err != nil {
		log.Printf("accounts: %s: %v", pluginName, err)
	}
	return []string{plugins.AccountUsernameEnvKey + "=" + found.Username, plugins.AccountSessionEnvKey + "=" + session}
}

// Login checks the credentials with the plugin and saves them only when the site accepted them.
func (s *Service) Login(ctx context.Context, p *plugins.Plugin, username string, password string) error {
	result, err := s.runner.Login(ctx, p, plugins.LoginArgs{Username: username, Password: password})
	if err != nil {
		return err
	}
	sealedPassword, err := s.box.Seal(password)
	if err != nil {
		return err
	}
	return s.saveSession(ctx, account{Plugin: p.Meta.Name, Username: username, Password: sealedPassword}, result)
}

// Logout forgets the plugin's account.
func (s *Service) Logout(ctx context.Context, pluginName string) error {
	if _, err := s.database.Exec(ctx, `DELETE FROM plugin_account WHERE plugin = $plugin`, db.Vars{"plugin": pluginName}); err != nil {
		return err
	}
	s.mutex.Lock()
	delete(s.accounts, pluginName)
	s.mutex.Unlock()
	return nil
}

// Relogin logs in again with the saved password after the plugin found its session expired.
func (s *Service) Relogin(ctx context.Context, p *plugins.Plugin) error {
	s.reloginMutex.Lock()
	defer s.reloginMutex.Unlock()
	if s.renewedRecently(p.Meta.Name) {
		return nil
	}
	saved, ok := s.lookup(p.Meta.Name)
	if !ok {
		return &plugins.PluginError{Plugin: p.Meta.Name, Message: "log in to this site in the plugin's settings", Code: plugins.ErrorCodeLoggedOut}
	}
	password, err := s.box.Open(saved.Password)
	if err != nil {
		return err
	}
	result, err := s.runner.Login(ctx, p, plugins.LoginArgs{Username: saved.Username, Password: password})
	if err != nil {
		s.saveError(ctx, saved, err)
		return err
	}
	s.mutex.Lock()
	s.renewedAt[p.Meta.Name] = time.Now()
	s.mutex.Unlock()
	return s.saveSession(ctx, saved, result)
}

func (s *Service) renewedRecently(pluginName string) bool {
	s.mutex.RLock()
	defer s.mutex.RUnlock()
	return time.Since(s.renewedAt[pluginName]) < reloginCooldown
}

func (s *Service) saveSession(ctx context.Context, next account, result *plugins.LoginResult) error {
	sealedSession, err := s.box.Seal(result.Session)
	if err != nil {
		return err
	}
	next.Session = sealedSession
	next.SessionExpiresAt = result.ExpiresAt
	next.Error = ""
	return s.save(ctx, next)
}

// saveError keeps why logging in again failed, so the apps can show it.
func (s *Service) saveError(ctx context.Context, failed account, loginError error) {
	failed.Error = loginError.Error()
	if err := s.save(ctx, failed); err != nil {
		log.Printf("accounts: %s: %v", failed.Plugin, err)
	}
}

func (s *Service) save(ctx context.Context, next account) error {
	next.UpdatedAt = db.Now()
	_, err := s.database.Exec(ctx, `INSERT INTO plugin_account
		(plugin, username, password, session, session_expires_at, error, updated_at)
		VALUES ($plugin, $username, $password, $session, $session_expires_at, $error, $updated_at)
		ON CONFLICT (plugin) DO UPDATE SET username = excluded.username, password = excluded.password,
			session = excluded.session, session_expires_at = excluded.session_expires_at,
			error = excluded.error, updated_at = excluded.updated_at`,
		db.Vars{
			"plugin": next.Plugin, "username": next.Username, "password": next.Password, "session": next.Session,
			"session_expires_at": next.SessionExpiresAt, "error": next.Error, "updated_at": next.UpdatedAt,
		})
	if err != nil {
		return err
	}
	s.mutex.Lock()
	s.accounts[next.Plugin] = next
	s.mutex.Unlock()
	return nil
}
