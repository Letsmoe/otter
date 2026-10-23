package plugins

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/dop251/goja"
)

// timeoutProperty carries AbortSignal.timeout's milliseconds on the signal object.
const timeoutProperty = "__timeoutMilliseconds"

// maxResponseBytes caps a fetched body; plugins only read pages and API JSON.
const maxResponseBytes = 64 << 20

// fetcher performs the embedded runtime's HTTP requests.
type fetcher struct {
	following *http.Client // redirect: "follow" (the default)
	manual    *http.Client // redirect: "manual": the 3xx response itself is returned
}

// newFetcher creates the clients, sharing one connection pool.
func newFetcher() *fetcher {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	return &fetcher{
		following: &http.Client{Transport: transport},
		manual: &http.Client{
			Transport: transport,
			CheckRedirect: func(*http.Request, []*http.Request) error {
				return http.ErrUseLastResponse
			},
		},
	}
}

// fetchRequest is a fetch() call's arguments, read off the JS values.
type fetchRequest struct {
	url      string
	method   string
	headers  map[string]string
	body     string
	timeout  time.Duration
	redirect string
}

// fetchResponse is what a completed request hands back to the runtime.
type fetchResponse struct {
	status     int
	statusText string
	url        string
	redirected bool
	header     http.Header
	body       []byte
}

// fetch implements the global fetch(): the request runs on its own goroutine
// and the returned promise settles on the session loop.
func (s *session) fetch(input goja.Value, init goja.Value) *goja.Promise {
	promise, resolve, reject := s.vm.NewPromise()
	request, err := s.readFetchRequest(input, init)
	if err != nil {
		_ = reject(s.vm.NewTypeError(err.Error()))
		return promise
	}

	s.pending++
	go func() {
		response, err := s.fetcher.do(s.ctx, request)
		s.post(func() error {
			if err != nil {
				return reject(s.vm.NewTypeError("fetch failed: " + err.Error()))
			}
			return resolve(s.responseObject(response))
		})
	}()
	return promise
}

// readFetchRequest reads fetch(input, init) into a request: input is a URL
// string or URL object; init may set method, headers, body, signal, redirect.
func (s *session) readFetchRequest(input goja.Value, init goja.Value) (fetchRequest, error) {
	request := fetchRequest{url: input.String(), method: http.MethodGet, headers: map[string]string{}}
	if goja.IsUndefined(init) || goja.IsNull(init) {
		return request, nil
	}
	options := init.ToObject(s.vm)
	if method := options.Get("method"); isSet(method) {
		request.method = strings.ToUpper(method.String())
	}
	if body := options.Get("body"); isSet(body) {
		request.body = body.String()
	}
	if redirect := options.Get("redirect"); isSet(redirect) {
		request.redirect = redirect.String()
	}
	if headers := options.Get("headers"); isSet(headers) {
		object := headers.ToObject(s.vm)
		for _, name := range object.Keys() {
			request.headers[name] = object.Get(name).String()
		}
	}
	if signal := options.Get("signal"); isSet(signal) {
		if milliseconds := signal.ToObject(s.vm).Get(timeoutProperty); isSet(milliseconds) {
			request.timeout = time.Duration(milliseconds.ToInteger()) * time.Millisecond
		}
	}
	return request, nil
}

// isSet reports whether an optional JS value was given.
func isSet(value goja.Value) bool {
	return value != nil && !goja.IsUndefined(value) && !goja.IsNull(value)
}

// do performs the request and reads the whole body.
func (f *fetcher) do(ctx context.Context, request fetchRequest) (*fetchResponse, error) {
	if request.timeout > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, request.timeout)
		defer cancel()
	}
	var body io.Reader
	if request.body != "" {
		body = strings.NewReader(request.body)
	}
	httpRequest, err := http.NewRequestWithContext(ctx, request.method, request.url, body)
	if err != nil {
		return nil, err
	}
	for name, value := range request.headers {
		// Leave compression to Go's transport, which only decodes what it asked for.
		if strings.EqualFold(name, "Accept-Encoding") {
			continue
		}
		httpRequest.Header.Set(name, value)
	}

	client := f.following
	if request.redirect == "manual" {
		client = f.manual
	}
	httpResponse, err := client.Do(httpRequest)
	if err != nil {
		if errors.Is(ctx.Err(), context.DeadlineExceeded) {
			return nil, fmt.Errorf("timed out after %s", request.timeout)
		}
		return nil, err
	}
	defer httpResponse.Body.Close()
	data, err := io.ReadAll(io.LimitReader(httpResponse.Body, maxResponseBytes))
	if err != nil {
		return nil, err
	}
	finalURL := httpResponse.Request.URL.String()
	return &fetchResponse{
		status:     httpResponse.StatusCode,
		statusText: strings.TrimSpace(strings.TrimPrefix(httpResponse.Status, fmt.Sprint(httpResponse.StatusCode))),
		url:        finalURL,
		redirected: finalURL != request.url,
		header:     httpResponse.Header,
		body:       data,
	}, nil
}

// responseObject builds the JS Response: status fields, headers, and the
// text/json/arrayBuffer body readers.
func (s *session) responseObject(response *fetchResponse) *goja.Object {
	object := s.vm.NewObject()
	_ = object.Set("ok", response.status >= 200 && response.status < 300)
	_ = object.Set("status", response.status)
	_ = object.Set("statusText", response.statusText)
	_ = object.Set("url", response.url)
	_ = object.Set("redirected", response.redirected)
	_ = object.Set("headers", s.headersObject(response.header))
	_ = object.Set("text", func() *goja.Promise {
		return s.settled(s.vm.ToValue(string(response.body)), nil)
	})
	_ = object.Set("json", func() *goja.Promise {
		return s.settled(s.parseJSON(string(response.body)))
	})
	_ = object.Set("arrayBuffer", func() *goja.Promise {
		return s.settled(s.vm.ToValue(s.vm.NewArrayBuffer(response.body)), nil)
	})
	return object
}

// headersObject is a read-only Headers: get (comma-joined), has, forEach.
func (s *session) headersObject(header http.Header) *goja.Object {
	object := s.vm.NewObject()
	_ = object.Set("get", func(name string) goja.Value {
		values := header.Values(name)
		if len(values) == 0 {
			return goja.Null()
		}
		return s.vm.ToValue(strings.Join(values, ", "))
	})
	_ = object.Set("has", func(name string) bool {
		return len(header.Values(name)) > 0
	})
	_ = object.Set("forEach", func(callback goja.Callable) error {
		for name, values := range header {
			_, err := callback(goja.Undefined(), s.vm.ToValue(strings.Join(values, ", ")), s.vm.ToValue(strings.ToLower(name)))
			if err != nil {
				return err
			}
		}
		return nil
	})
	return object
}

// parseJSON runs JSON.parse, returning the thrown value as the error.
func (s *session) parseJSON(text string) (goja.Value, error) {
	parse, _ := goja.AssertFunction(s.vm.Get("JSON").ToObject(s.vm).Get("parse"))
	return parse(goja.Undefined(), s.vm.ToValue(text))
}

// settled returns a promise already resolved with value, or rejected with err.
func (s *session) settled(value goja.Value, err error) *goja.Promise {
	promise, resolve, reject := s.vm.NewPromise()
	if err == nil {
		_ = resolve(value)
		return promise
	}
	var exception *goja.Exception
	if errors.As(err, &exception) {
		_ = reject(exception.Value())
		return promise
	}
	_ = reject(s.vm.NewGoError(err))
	return promise
}
