package neoworks

import (
	"bytes"
	"context"
	"errors"
	"io"
	"net/http"

	libneoworks "github.com/neoworks-dev/libneoworks-go"
)

// maxResponseBytes caps a sync answer; pull pages are far smaller.
const maxResponseBytes = 64 << 20

// transport performs libneoworks' requests: API paths on Neoworks with the
// account's access token, other URLs (presigned blob uploads) as they are.
type transport struct {
	service *Service
}

var _ libneoworks.Transport = transport{}

func (t transport) Do(ctx context.Context, request libneoworks.Request) (libneoworks.Response, error) {
	address := request.URL
	if request.IsAPI {
		address = t.service.endpoints.API + request.URL
	}
	outgoing, err := http.NewRequestWithContext(ctx, request.Method, address, bytes.NewReader(request.Body))
	if err != nil {
		return libneoworks.Response{}, err
	}
	if request.ContentType != "" {
		outgoing.Header.Set("Content-Type", request.ContentType)
	}
	if err := t.authorize(ctx, outgoing, request.IsAPI); err != nil {
		return libneoworks.Response{}, err
	}
	return t.send(outgoing)
}

// authorize adds the access token to API requests; while signed out they go
// without one, which the public routes (the schema registry) accept.
func (t transport) authorize(ctx context.Context, outgoing *http.Request, isAPI bool) error {
	if !isAPI {
		return nil
	}
	token, err := t.service.AccessToken(ctx)
	if errors.Is(err, ErrSignedOut) {
		return nil
	}
	if err != nil {
		return err
	}
	outgoing.Header.Set("Authorization", "Bearer "+token)
	return nil
}

func (t transport) send(outgoing *http.Request) (libneoworks.Response, error) {
	response, err := t.service.httpClient.Do(outgoing)
	if err != nil {
		return libneoworks.Response{}, err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(io.LimitReader(response.Body, maxResponseBytes))
	if err != nil {
		return libneoworks.Response{}, err
	}
	return libneoworks.Response{Status: response.StatusCode, Body: body}, nil
}
