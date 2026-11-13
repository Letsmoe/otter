package errorreport

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestCaptureWithoutStartDoesNothing(t *testing.T) {
	Capture(errors.New("boom"), "test", map[string]any{"key": "value"})
	Close()
}

func TestRecoverPanicsAgainSoNetHTTPHandlesItAsBefore(t *testing.T) {
	handler := Recover(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		panic("boom")
	}))
	defer func() {
		if recovered := recover(); recovered != "boom" {
			t.Fatalf("recovered %v, want the original panic", recovered)
		}
	}()
	handler.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/", nil))
}

func TestErrorReportingEnvironmentSwitch(t *testing.T) {
	t.Setenv("ERROR_REPORTING", "false")
	if Enabled() {
		t.Fatal("ERROR_REPORTING=false should turn reporting off")
	}
	t.Setenv("ERROR_REPORTING", "")
	if !Enabled() {
		t.Fatal("reporting should be on by default")
	}
}
