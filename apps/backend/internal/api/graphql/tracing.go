package graphql

import (
	"context"
	"errors"

	gqlgen "github.com/99designs/gqlgen/graphql"
	"github.com/vektah/gqlparser/v2/ast"
	"go.opentelemetry.io/otel/attribute"

	"github.com/Letsmoe/otter/internal/tracing"
)

// traceOperation records each query and mutation with its variables; a
// response with errors sends the trace. Subscriptions live as long as the
// app and aren't traced.
func traceOperation(ctx context.Context, next gqlgen.OperationHandler) gqlgen.ResponseHandler {
	operation := gqlgen.GetOperationContext(ctx)
	if operation.Operation == nil || operation.Operation.Operation == ast.Subscription {
		return next(ctx)
	}
	ctx, span := tracing.StartSpan(ctx, "graphql "+operationName(operation),
		attribute.String("graphql.operation.type", string(operation.Operation.Operation)),
		attribute.String("graphql.operation.name", operation.OperationName),
		tracing.JSON("graphql.variables", operation.Variables),
	)
	handler := next(ctx)
	return func(ctx context.Context) *gqlgen.Response {
		defer span.End()
		response := handler(ctx)
		if response != nil && len(response.Errors) > 0 {
			tracing.Fail(span, errors.New(response.Errors.Error()))
		}
		return response
	}
}

func operationName(operation *gqlgen.OperationContext) string {
	if operation.OperationName != "" {
		return operation.OperationName
	}
	return string(operation.Operation.Operation)
}
