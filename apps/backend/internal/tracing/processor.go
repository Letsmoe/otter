package tracing

import (
	"context"
	"sync"
	"time"

	"go.opentelemetry.io/otel/codes"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
)

// keepAttribute marks a span whose whole trace is sent even without a failure (TV requests).
const keepAttribute = "otter.keep"

const (
	// A long stream makes thousands of segment fetches: the first and the last ones are kept.
	firstSpansKept = 100
	lastSpansKept  = 200
	// Traces whose root never ends (a crash mid-request) must not grow without bound.
	maxBufferedTraces = 1000
	staleTraceAge     = 30 * time.Minute
)

// failedTraceProcessor holds every span of a trace until its local root ends,
// then hands the trace to next only when a span failed or asked to be kept.
// Traces whose root asks to be kept from the start (TV requests, which can
// run for hours) skip the buffer: each span goes to next as soon as it ends.
type failedTraceProcessor struct {
	next    sdktrace.SpanProcessor
	enabled func() bool
	mutex   sync.Mutex
	traces  map[trace.TraceID]*bufferedTrace
	// streaming are the traces sent span by span.
	streaming map[trace.TraceID]bool
}

type bufferedTrace struct {
	first      []sdktrace.ReadOnlySpan
	last       []sdktrace.ReadOnlySpan
	keep       bool
	lastSpanAt time.Time
}

func newFailedTraceProcessor(next sdktrace.SpanProcessor, enabled func() bool) *failedTraceProcessor {
	return &failedTraceProcessor{
		next:      next,
		enabled:   enabled,
		traces:    map[trace.TraceID]*bufferedTrace{},
		streaming: map[trace.TraceID]bool{},
	}
}

// OnStart notes traces whose root is kept anyway, so they are sent span by span.
func (processor *failedTraceProcessor) OnStart(_ context.Context, span sdktrace.ReadWriteSpan) {
	if !isLocalRoot(span) || !hasKeepAttribute(span) {
		return
	}
	processor.mutex.Lock()
	processor.streaming[span.SpanContext().TraceID()] = true
	processor.mutex.Unlock()
}

func (processor *failedTraceProcessor) OnEnd(span sdktrace.ReadOnlySpan) {
	if processor.sendStreamed(span) {
		return
	}
	buffered := processor.buffer(span)
	if buffered == nil || !isLocalRoot(span) {
		return
	}
	if !buffered.keep || !processor.enabled() {
		return
	}
	for _, bufferedSpan := range buffered.spans() {
		processor.next.OnEnd(bufferedSpan)
	}
}

// sendStreamed passes on a span of a streaming trace right away; false for other traces.
func (processor *failedTraceProcessor) sendStreamed(span sdktrace.ReadOnlySpan) bool {
	traceID := span.SpanContext().TraceID()
	processor.mutex.Lock()
	streaming := processor.streaming[traceID]
	if streaming && isLocalRoot(span) {
		delete(processor.streaming, traceID)
	}
	processor.mutex.Unlock()
	if !streaming {
		return false
	}
	if processor.enabled() {
		processor.next.OnEnd(span)
	}
	return true
}

// buffer adds span to its trace; for a local root it removes and returns the whole trace.
func (processor *failedTraceProcessor) buffer(span sdktrace.ReadOnlySpan) *bufferedTrace {
	processor.mutex.Lock()
	defer processor.mutex.Unlock()
	traceID := span.SpanContext().TraceID()
	buffered, found := processor.traces[traceID]
	if !found {
		if len(processor.traces) >= maxBufferedTraces {
			processor.dropStaleTraces()
		}
		if len(processor.traces) >= maxBufferedTraces {
			return nil
		}
		buffered = &bufferedTrace{}
		processor.traces[traceID] = buffered
	}
	buffered.add(span)
	buffered.lastSpanAt = time.Now()
	if isLocalRoot(span) {
		delete(processor.traces, traceID)
	}
	return buffered
}

// dropStaleTraces forgets traces whose root will likely never end, e.g. spans
// of background work that outlived its request.
func (processor *failedTraceProcessor) dropStaleTraces() {
	for traceID, buffered := range processor.traces {
		if time.Since(buffered.lastSpanAt) > staleTraceAge {
			delete(processor.traces, traceID)
		}
	}
}

func (buffered *bufferedTrace) add(span sdktrace.ReadOnlySpan) {
	if span.Status().Code == codes.Error || hasKeepAttribute(span) {
		buffered.keep = true
	}
	if len(buffered.first) < firstSpansKept {
		buffered.first = append(buffered.first, span)
		return
	}
	buffered.last = append(buffered.last, span)
	if len(buffered.last) > lastSpansKept {
		buffered.last = buffered.last[1:]
	}
}

func (buffered *bufferedTrace) spans() []sdktrace.ReadOnlySpan {
	return append(buffered.first, buffered.last...)
}

func isLocalRoot(span sdktrace.ReadOnlySpan) bool {
	parent := span.Parent()
	return !parent.IsValid() || parent.IsRemote()
}

func hasKeepAttribute(span sdktrace.ReadOnlySpan) bool {
	for _, attribute := range span.Attributes() {
		if string(attribute.Key) == keepAttribute && attribute.Value.AsBool() {
			return true
		}
	}
	return false
}

func (processor *failedTraceProcessor) Shutdown(ctx context.Context) error {
	return processor.next.Shutdown(ctx)
}

func (processor *failedTraceProcessor) ForceFlush(ctx context.Context) error {
	return processor.next.ForceFlush(ctx)
}
