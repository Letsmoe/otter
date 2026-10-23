package plugins

import (
	"slices"
	"testing"
)

// newTestRegistry builds a registry around in-memory plugins, skipping disk
// loading and the file watcher.
func newTestRegistry(hasSolver bool, loaded ...PluginMeta) *Registry {
	registry := &Registry{plugins: map[string]*Plugin{}, disabled: map[string]bool{}, hasSolver: hasSolver}
	for _, meta := range loaded {
		registry.plugins[meta.Name] = &Plugin{Meta: meta}
	}
	return registry
}

func TestSolverPluginsUnavailableWithoutSolver(t *testing.T) {
	plain := PluginMeta{Name: "plain", Capabilities: []Capability{CapabilityStreams}}
	blocked := PluginMeta{Name: "blocked", Capabilities: []Capability{CapabilityStreams}, RequiresSolver: true}

	withoutSolver := newTestRegistry(false, plain, blocked)
	withoutSolver.SetEnabled("plain", false)
	if withoutSolver.IsAvailable("blocked") || withoutSolver.IsActive("blocked") {
		t.Fatal("solver plugin must be unavailable without a solver")
	}
	if len(withoutSolver.WithCapability(CapabilityStreams)) != 0 {
		t.Fatal("disabled and unavailable plugins must not be listed")
	}
	inactive := withoutSolver.InactiveNames()
	slices.Sort(inactive)
	if !slices.Equal(inactive, []string{"blocked", "plain"}) {
		t.Fatalf("inactive = %v", inactive)
	}
	if !slices.Equal(withoutSolver.DisabledNames(), []string{"plain"}) {
		t.Fatalf("persisted disabled list must only hold user choices: %v", withoutSolver.DisabledNames())
	}

	withSolver := newTestRegistry(true, plain, blocked)
	if !withSolver.IsActive("blocked") || len(withSolver.WithCapability(CapabilityStreams)) != 2 {
		t.Fatal("solver plugin must be active when a solver is configured")
	}
}
