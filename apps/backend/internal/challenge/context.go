package challenge

import "context"

type interactiveKey struct{}

// WithInteractive marks ctx as coming from someone waiting to play: a check
// that fails hidden may then be shown to them.
func WithInteractive(ctx context.Context) context.Context {
	return context.WithValue(ctx, interactiveKey{}, true)
}

// IsInteractive reports whether ctx was marked by WithInteractive.
func IsInteractive(ctx context.Context) bool {
	interactive, _ := ctx.Value(interactiveKey{}).(bool)
	return interactive
}
