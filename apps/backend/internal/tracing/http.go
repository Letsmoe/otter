package tracing

import (
	"fmt"
	"net/http"
	"strings"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/trace"
)

// Middleware starts a span per request. server names the listener ("api",
// "lan"); while keepAll returns true every trace of it is sent, failed or not (TV requests).
func Middleware(server string, keepAll func() bool, next http.Handler) http.Handler {
	return http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		// A websocket lives as long as the app: its operations become their own traces.
		if request.Header.Get("Upgrade") != "" {
			next.ServeHTTP(writer, request)
			return
		}
		ctx, span := tracer.Start(request.Context(), request.Method+" "+request.URL.Path,
			trace.WithSpanKind(trace.SpanKindServer),
			trace.WithAttributes(requestAttributes(server, keepAll(), request)...),
		)
		recorder := &responseRecorder{ResponseWriter: writer, status: http.StatusOK}
		defer func() {
			recovered := recover()
			finishRequestSpan(span, recorder, request, recovered)
			if recovered != nil {
				panic(recovered)
			}
		}()
		next.ServeHTTP(recorder, request.WithContext(ctx))
	})
}

func requestAttributes(server string, keepAll bool, request *http.Request) []attribute.KeyValue {
	attributes := []attribute.KeyValue{
		attribute.String("otter.server", server),
		attribute.String("http.request.method", request.Method),
		attribute.String("url.path", request.URL.Path),
		Text("url.query", request.URL.RawQuery),
		attribute.String("client.address", request.RemoteAddr),
		attribute.String("user_agent.original", request.UserAgent()),
	}
	if keepAll {
		attributes = append(attributes, attribute.Bool(keepAttribute, true))
	}
	if castID := request.URL.Query().Get("cast"); castID != "" {
		attributes = append(attributes, attribute.String("otter.cast_id", castID))
	}
	return append(attributes, HeaderAttributes("http.request.header.", request.Header)...)
}

// HeaderAttributes records every header, e.g. what a TV asks for (Range, getcontentFeatures.dlna.org).
func HeaderAttributes(prefix string, header http.Header) []attribute.KeyValue {
	attributes := make([]attribute.KeyValue, 0, len(header))
	for name, values := range header {
		attributes = append(attributes, Text(prefix+strings.ToLower(name), strings.Join(values, ", ")))
	}
	return attributes
}

func finishRequestSpan(span trace.Span, recorder *responseRecorder, request *http.Request, recovered any) {
	defer span.End()
	span.SetAttributes(
		attribute.Int("http.response.status_code", recorder.status),
		attribute.Int64("http.response.body.size", recorder.bytes),
	)
	span.SetAttributes(HeaderAttributes("http.response.header.", recorder.Header())...)
	if request.Context().Err() != nil {
		span.SetAttributes(attribute.Bool("otter.client_disconnected", true))
	}
	if recovered != nil {
		Fail(span, fmt.Errorf("panic: %v", recovered))
		return
	}
	if recorder.status >= http.StatusInternalServerError {
		span.SetStatus(codes.Error, http.StatusText(recorder.status))
	}
}

// responseRecorder notes the status and body size and keeps streams flushing.
type responseRecorder struct {
	http.ResponseWriter
	status      int
	bytes       int64
	wroteHeader bool
}

func (recorder *responseRecorder) WriteHeader(status int) {
	if !recorder.wroteHeader {
		recorder.status = status
		recorder.wroteHeader = true
	}
	recorder.ResponseWriter.WriteHeader(status)
}

func (recorder *responseRecorder) Write(data []byte) (int, error) {
	recorder.wroteHeader = true
	written, err := recorder.ResponseWriter.Write(data)
	recorder.bytes += int64(written)
	return written, err
}

func (recorder *responseRecorder) Flush() {
	if flusher, ok := recorder.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

func (recorder *responseRecorder) Unwrap() http.ResponseWriter {
	return recorder.ResponseWriter
}

// Transport records outgoing requests made within a traced request. A failed
// fetch alone doesn't send the trace: plugins probe sites that are often down.
func Transport(base http.RoundTripper) http.RoundTripper {
	return &tracingTransport{base: base}
}

type tracingTransport struct {
	base http.RoundTripper
}

func (transport *tracingTransport) RoundTrip(request *http.Request) (*http.Response, error) {
	if !trace.SpanFromContext(request.Context()).SpanContext().IsValid() {
		return transport.base.RoundTrip(request)
	}
	ctx, span := tracer.Start(request.Context(), "fetch "+request.Method+" "+request.URL.Host,
		trace.WithSpanKind(trace.SpanKindClient),
		trace.WithAttributes(Text("url.full", request.URL.String()), attribute.String("http.request.method", request.Method)),
	)
	defer span.End()
	span.SetAttributes(HeaderAttributes("http.request.header.", request.Header)...)
	response, err := transport.base.RoundTrip(request.WithContext(ctx))
	if err != nil {
		span.RecordError(err)
		span.SetAttributes(attribute.String("error.type", fmt.Sprintf("%T", err)))
		return response, err
	}
	span.SetAttributes(attribute.Int("http.response.status_code", response.StatusCode))
	span.SetAttributes(HeaderAttributes("http.response.header.", response.Header)...)
	return response, nil
}
