package errorreport

import (
	"context"
	"errors"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestCaptureWithoutStartDoesNothing(t *testing.T) {
	Capture(context.Background(), errors.New("boom"), "test", map[string]any{"key": "value"})
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

func TestRecentLogsKeepTheLastLines(t *testing.T) {
	recent := &recentLog{}
	logger := log.New(recent, "", 0)
	for line := 0; line < recentLogLines+5; line++ {
		logger.Printf("line %d", line)
	}
	lines := strings.Split(recent.text(), "\n")
	if len(lines) != recentLogLines {
		t.Fatalf("kept %d lines, want %d", len(lines), recentLogLines)
	}
	if lines[0] != "line 5" || lines[len(lines)-1] != "line 204" {
		t.Fatalf("kept %q … %q, want the last lines", lines[0], lines[len(lines)-1])
	}
}
