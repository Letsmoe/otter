package main

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestIsPrivateClient(t *testing.T) {
	cases := map[string]bool{
		"127.0.0.1:5000":    true,
		"192.168.1.20:5000": true,
		"10.0.0.7:5000":     true,
		"[fe80::1]:5000":    true,
		"[::1]:5000":        true,
		"8.8.8.8:5000":      false,
		"[2001:db8::1]:80":  false,
		"not-an-address":    false,
	}
	for remoteAddress, want := range cases {
		if got := isPrivateClient(remoteAddress); got != want {
			t.Errorf("isPrivateClient(%q) = %v, want %v", remoteAddress, got, want)
		}
	}
}

func TestDlnaHeadersPointSamsungTVsAtTheCaptionFile(t *testing.T) {
	handler := dlnaHeaders(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.WriteHeader(http.StatusOK)
	}))
	subtitleID := "0123456789abcdef0123456789abcdef"

	request := httptest.NewRequest(http.MethodGet, "http://192.168.1.5:8081/api/stream/mp4?url=x&subtitle="+subtitleID, nil)
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)
	want := "http://192.168.1.5:8081/api/subtitles/" + subtitleID + ".srt"
	if got := recorder.Header().Get("CaptionInfo.sec"); got != want {
		t.Errorf("CaptionInfo.sec = %q, want %q", got, want)
	}

	for _, target := range []string{"/api/stream/mp4?url=x", "/api/stream/mp4?subtitle=../etc/passwd"} {
		recorder = httptest.NewRecorder()
		handler.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, target, nil))
		if got := recorder.Header().Get("CaptionInfo.sec"); got != "" {
			t.Errorf("%s: unexpected CaptionInfo.sec %q", target, got)
		}
	}
}
