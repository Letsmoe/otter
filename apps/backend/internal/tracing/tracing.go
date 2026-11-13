// Package tracing records what the backend did for each request as
// OpenTelemetry spans and sends a trace to PostHog only when something in it
// failed or it served a TV (see failedTraceProcessor).
package tracing

import (
	"context"
	"encoding/json"
	"log"
	"runtime"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"

	"github.com/Letsmoe/otter/internal/errorreport"
)

const tracesEndpoint = "https://eu.i.posthog.com/i/v1/traces"

// Long values (plugin arguments, headers) are cut to keep spans small.
const maxAttributeLength = 8 * 1024

var tracer = otel.Tracer("github.com/Letsmoe/otter")

var shutdown = func(context.Context) error { return nil }

// Start exports traces to PostHog; enabled is asked for every finished trace.
// Spans are recorded either way, so turning reporting on needs no restart.
func Start(ctx context.Context, enabled func() bool) {
	if !errorreport.Enabled() {
		return
	}
	exporter := newOTLPExporter(tracesEndpoint, errorreport.ProjectToken)
	processor := newFailedTraceProcessor(sdktrace.NewBatchSpanProcessor(exporter), enabled)
	provider := sdktrace.NewTracerProvider(sdktrace.WithResource(serviceResource()), sdktrace.WithSpanProcessor(processor))
	otel.SetTracerProvider(provider)
	shutdown = provider.Shutdown
}

// Close sends the traces still queued.
func Close(ctx context.Context) {
	if err := shutdown(ctx); err != nil {
		log.Printf("tracing: %v", err)
	}
}

func serviceResource() *resource.Resource {
	return resource.NewSchemaless(
		attribute.String("service.name", "otter-backend"),
		attribute.String("service.version", errorreport.BuildRevision()),
		attribute.String("otter.install_id", errorreport.InstallID()),
		attribute.String("os.type", runtime.GOOS),
		attribute.String("host.arch", runtime.GOARCH),
	)
}

// StartSpan starts a span as a child of the one in ctx.
func StartSpan(ctx context.Context, name string, attributes ...attribute.KeyValue) (context.Context, trace.Span) {
	return tracer.Start(ctx, name, trace.WithAttributes(attributes...))
}

// Fail marks span failed, which sends its whole trace.
func Fail(span trace.Span, err error) {
	span.RecordError(err)
	span.SetStatus(codes.Error, err.Error())
}

// JSON is value as a JSON attribute, cut to maxAttributeLength.
func JSON(key string, value any) attribute.KeyValue {
	encoded, err := json.Marshal(value)
	if err != nil {
		return attribute.String(key, err.Error())
	}
	return Text(key, string(encoded))
}

// SetJSON sets raw JSON (e.g. a plugin's result) on span, cut to maxAttributeLength.
func SetJSON(span trace.Span, key string, raw []byte) {
	span.SetAttributes(Text(key, string(raw)))
}

// Text is a string attribute cut to maxAttributeLength.
func Text(key string, value string) attribute.KeyValue {
	if len(value) > maxAttributeLength {
		value = value[:maxAttributeLength] + "…"
	}
	return attribute.String(key, value)
}
