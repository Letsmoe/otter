package graphql

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func profileSeenByHandler(t *testing.T, header string) (int, bool) {
	t.Helper()
	var profileID int
	var found bool
	handler := withRequestProfile(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		profileID, found = requestProfile(request.Context())
	}))
	request := httptest.NewRequest(http.MethodPost, "/graphql", nil)
	if header != "" {
		request.Header.Set(profileHeader, header)
	}
	handler.ServeHTTP(httptest.NewRecorder(), request)
	return profileID, found
}

func TestTheProfileHeaderReachesResolvers(t *testing.T) {
	profileID, found := profileSeenByHandler(t, "profile:3")

	if !found || profileID != 3 {
		t.Fatalf("got %d, %v", profileID, found)
	}
}

func TestRequestsWithoutAValidProfileHeaderActAsNobody(t *testing.T) {
	for _, header := range []string{"", "profile:x", "movie:603", "3"} {
		if _, found := profileSeenByHandler(t, header); found {
			t.Errorf("header %q was taken for a profile", header)
		}
	}
}

func TestAnOpenGateAllowsEverything(t *testing.T) {
	resolver := &Resolver{}

	allowed, err := resolver.allowedRefs(t.Context(), ageGate{}, nil)

	if err != nil || allowed != nil {
		t.Fatalf("got %v, %v", allowed, err)
	}
}
