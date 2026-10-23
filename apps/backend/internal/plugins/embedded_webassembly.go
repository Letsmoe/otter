package plugins

import (
	"context"
	"fmt"
	"time"

	"github.com/dop251/goja"
	"github.com/tetratelabs/wazero"
	"github.com/tetratelabs/wazero/api"
)

// The browser's WebAssembly API, reduced to what sites' decryptors need
// (vidsrc's vsdec.js): compile, instantiate without imports, call exports,
// and read and write linear memory through memory.buffer.
//
// Modules may not import anything, so they can only compute: no network, no
// files, no host calls. They run in wazero's interpreter (no executable
// memory, which Android restricts), with capped memory and a time limit per
// call.

// 64 KiB pages: 16 MiB is far more than a decryptor needs.
const wasmMemoryLimitPages = 256

// wasmCallTimeout ends calls that never return; a variable for tests.
var wasmCallTimeout = 5 * time.Second

// webAssembly is one session's WebAssembly global and the modules it compiled.
type webAssembly struct {
	session *session
	runtime wazero.Runtime
	// Compiled modules by the JS object handed out for them.
	modules map[*goja.Object]wazero.CompiledModule
}

func (s *session) newWebAssembly() *webAssembly {
	config := wazero.NewRuntimeConfigInterpreter().
		WithMemoryLimitPages(wasmMemoryLimitPages).
		WithCloseOnContextDone(true)
	return &webAssembly{
		session: s,
		runtime: wazero.NewRuntimeWithConfig(s.ctx, config),
		modules: map[*goja.Object]wazero.CompiledModule{},
	}
}

// close frees every module and instance of the session.
func (w *webAssembly) close() {
	_ = w.runtime.Close(context.Background())
}

func (w *webAssembly) object() *goja.Object {
	webAssembly := w.session.vm.NewObject()
	_ = webAssembly.Set("compile", w.compile)
	_ = webAssembly.Set("instantiate", w.instantiate)
	return webAssembly
}

// compile is WebAssembly.compile(bytes) -> Promise<Module>.
func (w *webAssembly) compile(bytes goja.Value) *goja.Promise {
	return w.settle(func() (goja.Value, error) {
		module, err := w.compileBytes(bytes)
		if err != nil {
			return nil, err
		}
		return module, nil
	})
}

// instantiate is WebAssembly.instantiate(module, imports) -> Promise<Instance>,
// or, given bytes, -> Promise<{module, instance}> like the browser.
func (w *webAssembly) instantiate(source goja.Value, imports goja.Value) *goja.Promise {
	return w.settle(func() (goja.Value, error) {
		if err := requireNoImports(imports); err != nil {
			return nil, err
		}
		module, isModule := w.moduleOf(source)
		if isModule {
			return w.newInstance(module)
		}
		return w.compileAndInstantiate(source)
	})
}

func (w *webAssembly) compileAndInstantiate(bytes goja.Value) (goja.Value, error) {
	module, err := w.compileBytes(bytes)
	if err != nil {
		return nil, err
	}
	instance, err := w.newInstance(w.modules[module])
	if err != nil {
		return nil, err
	}
	result := w.session.vm.NewObject()
	_ = result.Set("module", module)
	_ = result.Set("instance", instance)
	return result, nil
}

func (w *webAssembly) compileBytes(bytes goja.Value) (*goja.Object, error) {
	code, err := bytesOf(bytes)
	if err != nil {
		return nil, err
	}
	compiled, err := w.runtime.CompileModule(w.session.ctx, code)
	if err != nil {
		return nil, fmt.Errorf("WebAssembly.compile: %w", err)
	}
	if len(compiled.ImportedFunctions()) > 0 || len(compiled.ImportedMemories()) > 0 {
		_ = compiled.Close(w.session.ctx)
		return nil, fmt.Errorf("WebAssembly.compile: modules with imports are not supported")
	}
	module := w.session.vm.NewObject()
	w.modules[module] = compiled
	return module, nil
}

func (w *webAssembly) moduleOf(value goja.Value) (wazero.CompiledModule, bool) {
	object, isObject := value.(*goja.Object)
	if !isObject {
		return nil, false
	}
	compiled, found := w.modules[object]
	return compiled, found
}

// newInstance instantiates a fresh copy (own memory) with an exports object.
func (w *webAssembly) newInstance(compiled wazero.CompiledModule) (goja.Value, error) {
	// An empty name lets the same module be instantiated any number of times.
	config := wazero.NewModuleConfig().WithName("").WithStartFunctions()
	module, err := w.runtime.InstantiateModule(w.session.ctx, compiled, config)
	if err != nil {
		return nil, fmt.Errorf("WebAssembly.instantiate: %w", err)
	}
	instance := w.session.vm.NewObject()
	_ = instance.Set("exports", w.exportsOf(compiled, module))
	return instance, nil
}

func (w *webAssembly) exportsOf(compiled wazero.CompiledModule, module api.Module) *goja.Object {
	exports := w.session.vm.NewObject()
	for name := range compiled.ExportedFunctions() {
		_ = exports.Set(name, w.exportedFunction(module.ExportedFunction(name)))
	}
	for name := range compiled.ExportedMemories() {
		_ = exports.Set(name, w.memoryObject(module.ExportedMemory(name)))
	}
	return exports
}

// exportedFunction converts JS numbers to the function's parameter types and
// its result back; several results come back as an array.
func (w *webAssembly) exportedFunction(function api.Function) func(goja.FunctionCall) goja.Value {
	definition := function.Definition()
	return func(call goja.FunctionCall) goja.Value {
		parameters := make([]uint64, len(definition.ParamTypes()))
		for index, valueType := range definition.ParamTypes() {
			parameters[index] = encodeWasmValue(valueType, call.Argument(index))
		}
		ctx, cancel := context.WithTimeout(w.session.ctx, wasmCallTimeout)
		defer cancel()
		results, err := function.Call(ctx, parameters...)
		if err != nil {
			panic(w.session.vm.NewGoError(fmt.Errorf("WebAssembly call %s: %w", definition.Name(), err)))
		}
		return w.resultsValue(definition.ResultTypes(), results)
	}
}

func (w *webAssembly) resultsValue(types []api.ValueType, results []uint64) goja.Value {
	if len(results) == 0 {
		return goja.Undefined()
	}
	values := make([]any, len(results))
	for index, result := range results {
		values[index] = decodeWasmValue(types[index], result)
	}
	if len(values) == 1 {
		return w.session.vm.ToValue(values[0])
	}
	return w.session.vm.ToValue(values)
}

// memoryObject exposes memory.buffer as a live view: it is read on every
// access, since growing the memory moves it.
func (w *webAssembly) memoryObject(memory api.Memory) *goja.Object {
	object := w.session.vm.NewObject()
	getter := w.session.vm.ToValue(func() goja.Value {
		view, _ := memory.Read(0, memory.Size())
		return w.session.vm.ToValue(w.session.vm.NewArrayBuffer(view))
	})
	_ = object.DefineAccessorProperty("buffer", getter, nil, goja.FLAG_FALSE, goja.FLAG_TRUE)
	return object
}

// settle runs work at once and returns a promise of its outcome.
func (w *webAssembly) settle(work func() (goja.Value, error)) *goja.Promise {
	promise, resolve, reject := w.session.vm.NewPromise()
	value, err := work()
	if err != nil {
		_ = reject(w.session.vm.NewGoError(err))
		return promise
	}
	_ = resolve(value)
	return promise
}

// requireNoImports accepts a missing or empty import object.
func requireNoImports(imports goja.Value) error {
	if imports == nil || goja.IsUndefined(imports) || goja.IsNull(imports) {
		return nil
	}
	object, isObject := imports.(*goja.Object)
	if isObject && len(object.Keys()) == 0 {
		return nil
	}
	return fmt.Errorf("WebAssembly.instantiate: imports are not supported")
}

// bytesOf reads an ArrayBuffer or a typed array (Uint8Array, Buffer).
func bytesOf(value goja.Value) ([]byte, error) {
	switch exported := value.Export().(type) {
	case goja.ArrayBuffer:
		return exported.Bytes(), nil
	case []byte:
		return exported, nil
	}
	return nil, fmt.Errorf("WebAssembly.compile needs an ArrayBuffer or Uint8Array")
}

func encodeWasmValue(valueType api.ValueType, value goja.Value) uint64 {
	switch valueType {
	case api.ValueTypeI64:
		return uint64(value.ToInteger())
	case api.ValueTypeF32:
		return api.EncodeF32(float32(value.ToFloat()))
	case api.ValueTypeF64:
		return api.EncodeF64(value.ToFloat())
	}
	return api.EncodeI32(int32(value.ToInteger()))
}

func decodeWasmValue(valueType api.ValueType, value uint64) any {
	switch valueType {
	case api.ValueTypeI64:
		return int64(value)
	case api.ValueTypeF32:
		return api.DecodeF32(value)
	case api.ValueTypeF64:
		return api.DecodeF64(value)
	}
	return api.DecodeI32(value)
}
