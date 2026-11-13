package tracing

import (
	"context"

	"go.opentelemetry.io/otel/trace"
)

func spanOf(ctx context.Context) trace.Span {
	return trace.SpanFromContext(ctx)
}

// tracerOf starts children with the provider of the span in ctx.
func tracerOf(ctx context.Context) trace.Tracer {
	return spanOf(ctx).TracerProvider().Tracer("test")
}
