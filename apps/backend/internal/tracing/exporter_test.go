package tracing

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"go.opentelemetry.io/otel/attribute"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"
	tracepb "go.opentelemetry.io/proto/otlp/trace/v1"
	"google.golang.org/protobuf/proto"
)

// recordedSpans makes a request span with a failed plugin call below it.
func recordedSpans() []sdktrace.ReadOnlySpan {
	recorder := tracetest.NewSpanRecorder()
	testTracer := sdktrace.NewTracerProvider(sdktrace.WithSpanProcessor(recorder)).Tracer("otter")
	ctx, request := testTracer.Start(context.Background(), "GET /api/stream/continuous")
	request.SetAttributes(attribute.String("otter.cast_id", "abc"), attribute.Int("http.response.status_code", 200))
	_, plugin := testTracer.Start(ctx, "plugin streams kinoking")
	plugin.AddEvent("console", sdktraceEventAttribute("message", "no results"))
	Fail(plugin, errors.New("selector matched nothing"))
	plugin.End()
	request.End()
	return recorder.Ended()
}

func TestExporterPostsOTLPProtobuf(t *testing.T) {
	var received *tracepb.TracesData
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Header.Get("Content-Type") != "application/x-protobuf" || request.Header.Get("Authorization") != "Bearer phc_test" {
			t.Errorf("headers: %v", request.Header)
		}
		body, _ := io.ReadAll(request.Body)
		received = &tracepb.TracesData{}
		if err := proto.Unmarshal(body, received); err != nil {
			t.Errorf("decode: %v", err)
		}
	}))
	defer server.Close()

	spans := recordedSpans()
	if err := newOTLPExporter(server.URL, "phc_test").ExportSpans(context.Background(), spans); err != nil {
		t.Fatal(err)
	}
	sent := received.ResourceSpans[0].ScopeSpans[0].Spans
	if len(sent) != 2 {
		t.Fatalf("sent %d spans, want 2", len(sent))
	}
	plugin, request := sent[0], sent[1]
	if string(plugin.ParentSpanId) != string(request.SpanId) || string(plugin.TraceId) != string(request.TraceId) {
		t.Error("the plugin span lost its parent")
	}
	if plugin.Status.Code != tracepb.Status_STATUS_CODE_ERROR || plugin.Status.Message != "selector matched nothing" {
		t.Errorf("plugin status = %v", plugin.Status)
	}
	if plugin.Events[0].Name != "console" {
		t.Errorf("plugin events = %v", plugin.Events)
	}
	if request.Attributes[0].Value.GetStringValue() != "abc" || request.Attributes[1].Value.GetIntValue() != 200 {
		t.Errorf("request attributes = %v", request.Attributes)
	}
}

func TestExporterReportsRejectedSpans(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		http.Error(writer, "invalid token", http.StatusUnauthorized)
	}))
	defer server.Close()
	if err := newOTLPExporter(server.URL, "wrong").ExportSpans(context.Background(), recordedSpans()); err == nil {
		t.Fatal("a rejected export should be an error")
	}
}
