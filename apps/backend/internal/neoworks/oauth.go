package neoworks

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	libneoworks "github.com/neoworks-dev/libneoworks-go"
)

// Otter's registration on neoworks.dev: a public client, so PKCE stands in for a secret.
const (
	clientID    = "app_f25830013188c9a03b64"
	redirectURI = "https://otter.letsmoe.com/auth/callback"
	collection  = "@letsmoe/otter"
)

var scopes = []string{"openid", "profile", "email", collection + ":read", collection + ":write", collection + ":share"}

// Endpoints are the Neoworks servers; tests point them at a fake.
type Endpoints struct {
	// OAuth is the authorization server, e.g. https://oauth.neoworks.dev.
	OAuth string
	// API is the REST API including its version path, e.g. https://api.neoworks.dev/api/v1.
	API string
}

// Production is neoworks.dev.
var Production = Endpoints{OAuth: "https://oauth.neoworks.dev", API: "https://api.neoworks.dev/api/v1"}

// tokens is a token response; Grant is set on the authorization-code exchange.
type tokens struct {
	AccessToken  string          `json:"access_token"`
	RefreshToken string          `json:"refresh_token"`
	ExpiresIn    int             `json:"expires_in"`
	Grant        json.RawMessage `json:"neoworks_grant"`
}

// randomToken is 32 random bytes as base64url, for PKCE verifiers and state nonces.
func randomToken() (string, error) {
	random := make([]byte, 32)
	if _, err := rand.Read(random); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(random), nil
}

func codeChallenge(verifier string) string {
	digest := sha256.Sum256([]byte(verifier))
	return base64.RawURLEncoding.EncodeToString(digest[:])
}

// signInState tells otter.letsmoe.com/auth/callback (apps/share/src/auth.ts)
// where the code goes: "app.<nonce>" for the Android app, "web.<origin>.<nonce>"
// for a web app at returnOrigin.
func signInState(returnOrigin, nonce string) string {
	if returnOrigin == "" {
		return "app." + nonce
	}
	return "web." + base64.RawURLEncoding.EncodeToString([]byte(returnOrigin)) + "." + nonce
}

// authorizeURL asks for Otter's scopes for this install.
func authorizeURL(endpoints Endpoints, install libneoworks.InstallDescription, installName, state, verifier string) string {
	query := url.Values{}
	query.Set("response_type", "code")
	query.Set("client_id", clientID)
	query.Set("redirect_uri", redirectURI)
	query.Set("scope", strings.Join(scopes, " "))
	query.Set("state", state)
	query.Set("code_challenge", codeChallenge(verifier))
	query.Set("code_challenge_method", "S256")
	query.Set("install_id", install.InstallID)
	query.Set("install_enc_pub", install.InstallEncPub)
	query.Set("install_sign_pub", install.InstallSignPub)
	query.Set("install_name", installName)
	return endpoints.OAuth + "/oauth/authorize?" + query.Encode()
}

// exchangeCode trades the authorization code for tokens and the install's grant.
func exchangeCode(ctx context.Context, client *http.Client, endpoints Endpoints, code, verifier string) (tokens, error) {
	form := url.Values{}
	form.Set("grant_type", "authorization_code")
	form.Set("code", code)
	form.Set("redirect_uri", redirectURI)
	form.Set("client_id", clientID)
	form.Set("code_verifier", verifier)
	return postTokenForm(ctx, client, endpoints, form)
}

// refreshTokens gets a new access token with the refresh token.
func refreshTokens(ctx context.Context, client *http.Client, endpoints Endpoints, refreshToken string) (tokens, error) {
	form := url.Values{}
	form.Set("grant_type", "refresh_token")
	form.Set("refresh_token", refreshToken)
	form.Set("client_id", clientID)
	return postTokenForm(ctx, client, endpoints, form)
}

func postTokenForm(ctx context.Context, client *http.Client, endpoints Endpoints, form url.Values) (tokens, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoints.OAuth+"/oauth/token", strings.NewReader(form.Encode()))
	if err != nil {
		return tokens{}, err
	}
	request.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	var answer tokens
	if err := doJSON(client, request, &answer); err != nil {
		return tokens{}, fmt.Errorf("neoworks token: %w", err)
	}
	if answer.AccessToken == "" {
		return tokens{}, errors.New("neoworks token: no access token in the answer")
	}
	return answer, nil
}

// expiresAt is when an access token stops working, a minute early.
func expiresAt(answer tokens, now time.Time) time.Time {
	lifetime := time.Duration(answer.ExpiresIn) * time.Second
	if lifetime <= 0 {
		lifetime = 5 * time.Minute
	}
	return now.Add(lifetime - time.Minute)
}

// subjectOf reads the user id ("sub") from an access token. The token comes
// straight from the token endpoint over TLS, so its signature isn't checked here.
func subjectOf(accessToken string) (string, error) {
	parts := strings.Split(accessToken, ".")
	if len(parts) != 3 {
		return "", errors.New("neoworks: access token is not a JWT")
	}
	payload, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return "", fmt.Errorf("neoworks: access token payload: %w", err)
	}
	var claims struct {
		Subject string `json:"sub"`
	}
	if err := json.Unmarshal(payload, &claims); err != nil || claims.Subject == "" {
		return "", errors.New("neoworks: access token has no subject")
	}
	return claims.Subject, nil
}

// userInfo is the OpenID profile shown in Settings.
type userInfo struct {
	Email string `json:"email"`
	Name  string `json:"name"`
}

func fetchUserInfo(ctx context.Context, client *http.Client, endpoints Endpoints, accessToken string) (userInfo, error) {
	var info userInfo
	err := getJSON(ctx, client, endpoints.OAuth+"/oauth/userinfo", accessToken, &info)
	return info, err
}

// fetchOwnerIdentity is the account owner's public identity, which AcceptGrant
// pins and checks the grant's signature against.
func fetchOwnerIdentity(ctx context.Context, client *http.Client, endpoints Endpoints, accessToken, userID string) (json.RawMessage, error) {
	var identity json.RawMessage
	address := endpoints.API + "/keys/identity?userId=" + url.QueryEscape(userID)
	if err := getJSON(ctx, client, address, accessToken, &identity); err != nil {
		return nil, fmt.Errorf("neoworks identity: %w", err)
	}
	return identity, nil
}

func getJSON(ctx context.Context, client *http.Client, address, accessToken string, target any) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, address, nil)
	if err != nil {
		return err
	}
	request.Header.Set("Authorization", "Bearer "+accessToken)
	return doJSON(client, request, target)
}

func doJSON(client *http.Client, request *http.Request, target any) error {
	request.Header.Set("Accept", "application/json")
	response, err := client.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(io.LimitReader(response.Body, 1<<20))
	if err != nil {
		return err
	}
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d: %s", response.StatusCode, strings.TrimSpace(string(body)))
	}
	return json.Unmarshal(body, target)
}
