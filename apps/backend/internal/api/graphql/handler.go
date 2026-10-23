package graphql

import (
	"net/http"
	"time"

	gqlhandler "github.com/99designs/gqlgen/graphql/handler"
	"github.com/99designs/gqlgen/graphql/handler/extension"
	"github.com/99designs/gqlgen/graphql/handler/lru"
	"github.com/99designs/gqlgen/graphql/handler/transport"
	"github.com/gorilla/websocket"
	"github.com/vektah/gqlparser/v2/ast"

	"github.com/neoworks/otter/internal/api/graphql/generated"
)

// SchemaSDL is built from all embedded schema files at startup.
// The generated package keeps them as ast.Source entries; we concatenate inputs here.
var SchemaSDL string

func init() {
	// This relies on the fact that generated.go embeds every *.graphql source; we
	// reconstruct the SDL by introspecting the executable schema.
	SchemaSDL = "# see internal/api/graphql/schema/*.graphql"
}

// NewHandler returns an HTTP handler for the GraphQL endpoint.
//
// Transports are wired manually (rather than via NewDefaultServer) so the
// Websocket transport uses our permissive Upgrader. NewDefaultServer registers
// its own Websocket transport first — with gorilla's default same-origin
// CheckOrigin — which would otherwise win transport selection and reject the
// cross-origin (:3000 → :8080) subscription upgrade.
func NewHandler(resolver *Resolver) (http.Handler, error) {
	srv := gqlhandler.New(generated.NewExecutableSchema(generated.Config{
		Resolvers: resolver,
	}))

	// Local, no-auth app: accept any origin for the subscription upgrade.
	srv.AddTransport(transport.Websocket{
		KeepAlivePingInterval: 10 * time.Second,
		Upgrader: websocket.Upgrader{
			CheckOrigin:     func(r *http.Request) bool { return true },
			ReadBufferSize:  1024,
			WriteBufferSize: 1024,
		},
	})
	srv.AddTransport(transport.Options{})
	srv.AddTransport(transport.GET{})
	srv.AddTransport(transport.POST{})
	srv.AddTransport(transport.MultipartForm{})

	srv.SetQueryCache(lru.New[*ast.QueryDocument](1000))
	srv.Use(extension.Introspection{})
	srv.Use(extension.AutomaticPersistedQuery{Cache: lru.New[string](100)})

	return srv, nil
}
