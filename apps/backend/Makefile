.PHONY: generate generate-ts build

# Regenerate all code from the GraphQL schema.
# Run after editing internal/api/graphql/schema.graphql.
#
# To pull from a running server instead of the local file:
#   GQL_SCHEMA_URL=http://localhost:8080/api/schema make generate
generate: generate-ts

generate-ts:
	cd web && bun run codegen

build:
	go build ./...
	cd web && bun run build
