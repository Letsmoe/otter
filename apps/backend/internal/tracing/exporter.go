package tracing

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"time"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/sdk/instrumentation"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
	commonpb "go.opentelemetry.io/proto/otlp/common/v1"
	resourcepb "go.opentelemetry.io/proto/otlp/resource/v1"
	tracepb "go.opentelemetry.io/proto/otlp/trace/v1"
	"google.golang.org/protobuf/proto"
)

// otlpExporter posts spans as OTLP/HTTP protobuf. The official exporter links
// gRPC (5.7 MB in the Android binary); TracesData has the same wire format as
// ExportTraceServiceRequest, so it is sent as the request body instead.
type otlpExporter struct {
	endpoint string
	token    string
	client   *http.Client
}

func newOTLPExporter(endpoint string, token string) *otlpExporter {
	return &otlpExporter{endpoint: endpoint, token: token, client: &http.Client{Timeout: 30 * time.Second}}
}

func (exporter *otlpExporter) ExportSpans(ctx context.Context, spans []sdktrace.ReadOnlySpan) error {
	if len(spans) == 0 {
		return nil
	}
	body, err := proto.Marshal(tracesData(spans))
	if err != nil {
		return fmt.Errorf("encode spans: %w", err)
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, exporter.endpoint, bytes.NewReader(body))
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", "application/x-protobuf")
	request.Header.Set("Authorization", "Bearer "+exporter.token)
	response, err := exporter.client.Do(request)
	if err != nil {
		return fmt.Errorf("send spans: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode >= 300 {
		message, _ := io.ReadAll(io.LimitReader(response.Body, 1024))
		return fmt.Errorf("send spans: HTTP %d %s", response.StatusCode, message)
	}
	return nil
}

func (exporter *otlpExporter) Shutdown(context.Context) error {
	return nil
}

// tracesData groups spans by their resource and instrumentation scope.
func tracesData(spans []sdktrace.ReadOnlySpan) *tracepb.TracesData {
	data := &tracepb.TracesData{}
	scopes := map[instrumentation.Scope]*tracepb.ScopeSpans{}
	for _, span := range spans {
		scopeSpans, found := scopes[span.InstrumentationScope()]
		if !found {
			scopeSpans = &tracepb.ScopeSpans{Scope: &commonpb.InstrumentationScope{Name: span.InstrumentationScope().Name}}
			scopes[span.InstrumentationScope()] = scopeSpans
			data.ResourceSpans = append(data.ResourceSpans, &tracepb.ResourceSpans{
				Resource:   &resourcepb.Resource{Attributes: keyValues(span.Resource().Attributes())},
				ScopeSpans: []*tracepb.ScopeSpans{scopeSpans},
			})
		}
		scopeSpans.Spans = append(scopeSpans.Spans, spanMessage(span))
	}
	return data
}

func spanMessage(span sdktrace.ReadOnlySpan) *tracepb.Span {
	spanContext := span.SpanContext()
	traceID := spanContext.TraceID()
	spanID := spanContext.SpanID()
	message := &tracepb.Span{
		TraceId:           traceID[:],
		SpanId:            spanID[:],
		Name:              span.Name(),
		Kind:              spanKind(span.SpanKind()),
		StartTimeUnixNano: uint64(span.StartTime().UnixNano()),
		EndTimeUnixNano:   uint64(span.EndTime().UnixNano()),
		Attributes:        keyValues(span.Attributes()),
		Events:            events(span.Events()),
		Status:            status(span.Status()),
	}
	if span.Parent().IsValid() {
		parentID := span.Parent().SpanID()
		message.ParentSpanId = parentID[:]
	}
	return message
}

func events(spanEvents []sdktrace.Event) []*tracepb.Span_Event {
	messages := make([]*tracepb.Span_Event, 0, len(spanEvents))
	for _, event := range spanEvents {
		messages = append(messages, &tracepb.Span_Event{
			Name:         event.Name,
			TimeUnixNano: uint64(event.Time.UnixNano()),
			Attributes:   keyValues(event.Attributes),
		})
	}
	return messages
}

func status(spanStatus sdktrace.Status) *tracepb.Status {
	switch spanStatus.Code {
	case codes.Error:
		return &tracepb.Status{Code: tracepb.Status_STATUS_CODE_ERROR, Message: spanStatus.Description}
	case codes.Ok:
		return &tracepb.Status{Code: tracepb.Status_STATUS_CODE_OK}
	}
	return &tracepb.Status{}
}

func spanKind(kind trace.SpanKind) tracepb.Span_SpanKind {
	switch kind {
	case trace.SpanKindServer:
		return tracepb.Span_SPAN_KIND_SERVER
	case trace.SpanKindClient:
		return tracepb.Span_SPAN_KIND_CLIENT
	case trace.SpanKindProducer:
		return tracepb.Span_SPAN_KIND_PRODUCER
	case trace.SpanKindConsumer:
		return tracepb.Span_SPAN_KIND_CONSUMER
	}
	return tracepb.Span_SPAN_KIND_INTERNAL
}

func keyValues(attributes []attribute.KeyValue) []*commonpb.KeyValue {
	messages := make([]*commonpb.KeyValue, 0, len(attributes))
	for _, keyValue := range attributes {
		messages = append(messages, &commonpb.KeyValue{Key: string(keyValue.Key), Value: anyValue(keyValue.Value)})
	}
	return messages
}

// anyValue keeps scalars typed; slices are sent as their text form.
func anyValue(value attribute.Value) *commonpb.AnyValue {
	switch value.Type() {
	case attribute.BOOL:
		return &commonpb.AnyValue{Value: &commonpb.AnyValue_BoolValue{BoolValue: value.AsBool()}}
	case attribute.INT64:
		return &commonpb.AnyValue{Value: &commonpb.AnyValue_IntValue{IntValue: value.AsInt64()}}
	case attribute.FLOAT64:
		return &commonpb.AnyValue{Value: &commonpb.AnyValue_DoubleValue{DoubleValue: value.AsFloat64()}}
	case attribute.STRING:
		return &commonpb.AnyValue{Value: &commonpb.AnyValue_StringValue{StringValue: value.AsString()}}
	}
	return &commonpb.AnyValue{Value: &commonpb.AnyValue_StringValue{StringValue: value.String()}}
}
