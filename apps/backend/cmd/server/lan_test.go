package main

import "testing"

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
