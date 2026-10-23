package plugins

import (
	"context"
	"encoding/base64"
	"strings"
	"testing"
	"time"
)

// testModule exports memory, store(pointer, value) writing one byte,
// double(x) and spin(), an endless loop.
var testModule = []byte{
	0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
	// types: (i32, i32) -> (), (i32) -> i32, () -> ()
	0x01, 0x0e, 0x03, 0x60, 0x02, 0x7f, 0x7f, 0x00, 0x60, 0x01, 0x7f, 0x01, 0x7f, 0x60, 0x00, 0x00,
	// functions
	0x03, 0x04, 0x03, 0x00, 0x01, 0x02,
	// one page of memory
	0x05, 0x03, 0x01, 0x00, 0x01,
	// exports
	0x07, 0x22, 0x04,
	0x06, 'm', 'e', 'm', 'o', 'r', 'y', 0x02, 0x00,
	0x05, 's', 't', 'o', 'r', 'e', 0x00, 0x00,
	0x06, 'd', 'o', 'u', 'b', 'l', 'e', 0x00, 0x01,
	0x04, 's', 'p', 'i', 'n', 0x00, 0x02,
	// code
	0x0a, 0x1b, 0x03,
	0x09, 0x00, 0x20, 0x00, 0x20, 0x01, 0x3a, 0x00, 0x00, 0x0b,
	0x07, 0x00, 0x20, 0x00, 0x41, 0x02, 0x6c, 0x0b,
	0x07, 0x00, 0x03, 0x40, 0x0c, 0x00, 0x0b, 0x0b,
}

// importingModule imports env.f, which the runtime must refuse.
var importingModule = []byte{
	0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
	0x01, 0x04, 0x01, 0x60, 0x00, 0x00,
	0x02, 0x09, 0x01, 0x03, 'e', 'n', 'v', 0x01, 'f', 0x00, 0x00,
}

func runWebAssemblyScript(t *testing.T, script string, modules ...[]byte) string {
	t.Helper()
	encoded := make([]string, len(modules))
	for index, module := range modules {
		encoded[index] = `Buffer.from("` + base64.StdEncoding.EncodeToString(module) + `", "base64")`
	}
	source := "const modules = [" + strings.Join(encoded, ", ") + "];\n" + script + `
.then((result) => require("fs").writeSync(1, JSON.stringify({ result }) + "\n"))
.catch((error) => require("fs").writeSync(1, JSON.stringify({ result: "error: " + error.message }) + "\n"));`
	raw, err := NewEmbeddedRunner().invoke(context.Background(), writeBundle(t, source), "test", nil)
	if err != nil {
		t.Fatal(err)
	}
	return string(raw)
}

func TestWebAssemblyCallsExportsAndSharesMemory(t *testing.T) {
	raw := runWebAssemblyScript(t, `
WebAssembly.compile(modules[0])
  .then((module) => WebAssembly.instantiate(module, {}))
  .then((instance) => {
    const exports = instance.exports;
    exports.store(5, 42);
    const written = new Uint8Array(exports.memory.buffer)[5];
    new Uint8Array(exports.memory.buffer, 7, 1).set([9]);
    const readBack = new Uint8Array(exports.memory.buffer)[7];
    return [exports.double(21), written, readBack, exports.memory.buffer.byteLength];
  })`, testModule)
	if raw != `[42,42,9,65536]` {
		t.Fatalf("got %s", raw)
	}
}

func TestWebAssemblyInstantiatesFromBytesLikeTheBrowser(t *testing.T) {
	raw := runWebAssemblyScript(t, `
WebAssembly.instantiate(modules[0].buffer.slice(modules[0].byteOffset, modules[0].byteOffset + modules[0].length))
  .then((result) => [typeof result.module, result.instance.exports.double(4)])`, testModule)
	if raw != `["object",8]` {
		t.Fatalf("got %s", raw)
	}
}

func TestWebAssemblyRefusesImports(t *testing.T) {
	raw := runWebAssemblyScript(t, `WebAssembly.compile(modules[0])`, importingModule)
	if !strings.Contains(raw, "imports are not supported") {
		t.Fatalf("got %s", raw)
	}
	raw = runWebAssemblyScript(t, `
WebAssembly.compile(modules[0]).then((module) => WebAssembly.instantiate(module, { env: {} }))`, testModule)
	if !strings.Contains(raw, "imports are not supported") {
		t.Fatalf("got %s", raw)
	}
}

func TestWebAssemblyEndsEndlessCalls(t *testing.T) {
	previous := wasmCallTimeout
	wasmCallTimeout = 200 * time.Millisecond
	defer func() { wasmCallTimeout = previous }()

	started := time.Now()
	raw := runWebAssemblyScript(t, `
WebAssembly.instantiate(modules[0]).then((result) => {
  try {
    result.instance.exports.spin();
    return "returned";
  } catch (error) {
    return "stopped";
  }
})`, testModule)
	if raw != `"stopped"` || time.Since(started) > 5*time.Second {
		t.Fatalf("got %s after %s", raw, time.Since(started))
	}
}
