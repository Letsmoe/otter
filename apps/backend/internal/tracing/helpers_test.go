package tracing

import (
	"context"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"
)

func spanOf(ctx context.Context) trace.Span {
	return trace.SpanFromContext(ctx)
}

func sdktraceEventAttribute(key string, value string) trace.EventOption {
	return trace.WithAttributes(attribute.String(key, value))
}

// tracerOf starts children with the provider of the span in ctx.
func tracerOf(ctx context.Context) trace.Tracer {
	return spanOf(ctx).TracerProvider().Tracer("test")
}
