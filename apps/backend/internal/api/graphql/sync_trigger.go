package graphql

import (
	"context"

	gqlgen "github.com/99designs/gqlgen/graphql"
	"github.com/vektah/gqlparser/v2/ast"
)

// syncedMutations change data Neoworks syncs: profiles, what they watched,
// rated and listed, and the household's settings.
var syncedMutations = map[string]bool{
	"recordProgress": true, "setRating": true, "clearRating": true,
	"addToWatchlist": true, "removeFromWatchlist": true,
	"createProfile": true, "updateProfile": true, "deleteProfile": true,
	"updateSettings": true, "setPluginEnabled": true, "setPluginSettings": true,
}

// syncAfterChanges tells Neoworks sync about a successful mutation of synced
// data; it syncs once the changes pause.
func (r *Resolver) syncAfterChanges(ctx context.Context, next gqlgen.OperationHandler) gqlgen.ResponseHandler {
	operation := gqlgen.GetOperationContext(ctx)
	handler := next(ctx)
	if operation.Operation == nil || operation.Operation.Operation != ast.Mutation || !changesSyncedData(operation.Operation) {
		return handler
	}
	return func(ctx context.Context) *gqlgen.Response {
		response := handler(ctx)
		if response != nil && len(response.Errors) == 0 {
			r.Neoworks.NotifyChange()
		}
		return response
	}
}

func changesSyncedData(operation *ast.OperationDefinition) bool {
	for _, selection := range operation.SelectionSet {
		field, isField := selection.(*ast.Field)
		if isField && syncedMutations[field.Name] {
			return true
		}
	}
	return false
}
