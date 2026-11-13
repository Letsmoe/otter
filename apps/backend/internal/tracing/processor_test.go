package tracing

import (
	"context"
	"errors"
	"testing"

	"go.opentelemetry.io/otel/attribute"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
)

func newTestTracer(enabled bool) (*tracetest.InMemoryExporter, func(name string) (context.Context, func())) {
	exporter := tracetest.NewInMemoryExporter()
	processor := newFailedTraceProcessor(sdktrace.NewSimpleSpanProcessor(exporter), func() bool { return enabled })
	provider := sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(processor))
	testTracer := provider.Tracer("test")
	start := func(name string) (context.Context, func()) {
		ctx, span := testTracer.Start(context.Background(), name)
		return ctx, func() { span.End() }
	}
	return exporter, start
}

func TestSuccessfulTracesAreDropped(t *testing.T) {
	exporter, start := newTestTracer(true)
	ctx, endRoot := start("request")
	_, child := tracerOf(ctx).Start(ctx, "plugin")
	child.End()
	endRoot()
	if spans := exporter.GetSpans(); len(spans) != 0 {
		t.Fatalf("exported %d spans of a successful trace", len(spans))
	}
}

func TestAFailedChildSendsTheWholeTrace(t *testing.T) {
	exporter, start := newTestTracer(true)
	ctx, endRoot := start("request")
	_, child := tracerOf(ctx).Start(ctx, "plugin")
	Fail(child, errors.New("selector matched nothing"))
	child.End()
	if spans := exporter.GetSpans(); len(spans) != 0 {
		t.Fatal("spans were sent before the request ended")
	}
	endRoot()
	if spans := exporter.GetSpans(); len(spans) != 2 {
		t.Fatalf("exported %d spans, want the request and the plugin call", len(spans))
	}
}

func TestKeptTracesAreSentWithoutAFailure(t *testing.T) {
	exporter, start := newTestTracer(true)
	ctx, endRoot := start("GET /api/stream/continuous")
	rootSpan := spanOf(ctx)
	rootSpan.SetAttributes(attribute.Bool(keepAttribute, true))
	endRoot()
	if spans := exporter.GetSpans(); len(spans) != 1 {
		t.Fatalf("exported %d spans of a TV request, want 1", len(spans))
	}
}

func TestNothingIsSentWhileReportingIsOff(t *testing.T) {
	exporter, start := newTestTracer(false)
	ctx, endRoot := start("request")
	Fail(spanOf(ctx), errors.New("boom"))
	endRoot()
	if spans := exporter.GetSpans(); len(spans) != 0 {
		t.Fatalf("exported %d spans with reporting off", len(spans))
	}
}

func TestLongTracesKeepTheirFirstAndLastSpans(t *testing.T) {
	exporter, start := newTestTracer(true)
	ctx, endRoot := start("stream")
	for index := 0; index < 1000; index++ {
		_, segment := tracerOf(ctx).Start(ctx, "fetch segment")
		segment.End()
	}
	Fail(spanOf(ctx), errors.New("upstream gone"))
	endRoot()
	if spans := exporter.GetSpans(); len(spans) != firstSpansKept+lastSpansKept {
		t.Fatalf("exported %d spans, want %d", len(spans), firstSpansKept+lastSpansKept)
	}
}
