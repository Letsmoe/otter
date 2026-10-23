package plugins

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/dop251/goja"
	"github.com/dop251/goja_nodejs/buffer"
	"github.com/dop251/goja_nodejs/require"
	"github.com/dop251/goja_nodejs/url"
)

// embeddedExecutor runs plugins in goja, an in-process JS engine, for hosts
// without Bun (the phone app). Each plugin must be pre-bundled into one
// self-contained CommonJS file (`bun build --target=node --format=cjs`), which
// its package.json "main" points at.
//
// The runtime emulates the sliver of Bun/Node the SDK's runPlugin touches —
// Bun.stdin.text(), require("fs").writeSync, process.env and process.exit —
// so plugins and SDK run unchanged, and provides fetch, URL, URLSearchParams,
// AbortSignal.timeout and setTimeout on top of the language itself. There is
// no atob/btoa, TextDecoder or AbortController: use Buffer for base64.
type embeddedExecutor struct {
	fetcher *fetcher

	mu       sync.Mutex
	programs map[string]compiledBundle // by bundle path
}

// compiledBundle is a parsed plugin bundle, reused until the file changes.
type compiledBundle struct {
	modTime time.Time
	program *goja.Program
}

// newEmbeddedExecutor creates an executor with its own HTTP client.
func newEmbeddedExecutor() *embeddedExecutor {
	return &embeddedExecutor{fetcher: newFetcher(), programs: map[string]compiledBundle{}}
}

// install is a no-op: bundles carry their dependencies.
func (e *embeddedExecutor) install(ctx context.Context, dir string) error {
	return nil
}

// run executes the plugin bundle in a fresh runtime and returns what it wrote to stdout.
func (e *embeddedExecutor) run(ctx context.Context, p *Plugin, stdin []byte, env []string) ([]byte, error) {
	program, err := e.bundle(filepath.Join(p.Dir, p.EntryPoint))
	if err != nil {
		return nil, fmt.Errorf("plugin %q: %w", p.Meta.Name, err)
	}
	session := newSession(ctx, p.Meta.Name, e.fetcher, string(stdin), env)
	stdout, err := session.run(program)
	if err != nil {
		return nil, fmt.Errorf("plugin %q: %w", p.Meta.Name, err)
	}
	return stdout, nil
}

// bundle returns the compiled bundle at path, recompiling it when the file changed.
func (e *embeddedExecutor) bundle(path string) (*goja.Program, error) {
	info, err := os.Stat(path)
	if err != nil {
		return nil, err
	}
	e.mu.Lock()
	cached, ok := e.programs[path]
	e.mu.Unlock()
	if ok && cached.modTime.Equal(info.ModTime()) {
		return cached.program, nil
	}

	source, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	program, err := goja.Compile(path, string(source), false)
	if err != nil {
		return nil, fmt.Errorf("compile %s: %w", filepath.Base(path), err)
	}
	e.mu.Lock()
	e.programs[path] = compiledBundle{modTime: info.ModTime(), program: program}
	e.mu.Unlock()
	return program, nil
}

// exitSignal interrupts the runtime when the plugin calls process.exit.
type exitSignal struct{}

// session is one plugin invocation: a runtime, its stdout, and the loop that
// feeds async results (fetch responses) back into it.
type session struct {
	ctx        context.Context
	pluginName string
	fetcher    *fetcher
	stdin      string
	env        []string
	vm         *goja.Runtime

	stdout   bytes.Buffer
	exited   bool
	exitCode int

	// Callbacks from goroutines, run on the runtime's goroutine.
	jobs chan func() error
	// Async operations whose callback hasn't run yet; the session ends at zero.
	pending int
	// Closed when the session ends, so late goroutines don't block.
	done chan struct{}
	// The WebAssembly global's modules; freed when the session ends.
	webAssembly *webAssembly
}

// newSession prepares an invocation; nothing runs until run.
func newSession(ctx context.Context, pluginName string, fetcher *fetcher, stdin string, env []string) *session {
	return &session{
		ctx:        ctx,
		pluginName: pluginName,
		fetcher:    fetcher,
		stdin:      stdin,
		env:        env,
		jobs:       make(chan func() error),
		done:       make(chan struct{}),
	}
}

// run executes the program, then serves async callbacks until the plugin
// exits, nothing is pending any more, or the context ends.
func (s *session) run(program *goja.Program) ([]byte, error) {
	defer close(s.done)
	s.vm = goja.New()
	s.webAssembly = s.newWebAssembly()
	defer s.webAssembly.close()
	if err := s.installGlobals(); err != nil {
		return nil, err
	}

	stopInterrupting := context.AfterFunc(s.ctx, func() {
		s.vm.Interrupt(s.ctx.Err())
	})
	defer stopInterrupting()

	if _, err := s.vm.RunProgram(program); err != nil {
		return s.finish(err)
	}
	for !s.exited && s.pending > 0 {
		select {
		case job := <-s.jobs:
			s.pending--
			if err := job(); err != nil {
				return s.finish(err)
			}
		case <-s.ctx.Done():
			return nil, s.ctx.Err()
		}
	}
	return s.finish(nil)
}

// finish turns the end of the run into the result: process.exit's interrupt
// is a normal end; any other error or a non-zero exit code fails the call.
func (s *session) finish(err error) ([]byte, error) {
	var interrupted *goja.InterruptedError
	if errors.As(err, &interrupted) {
		if _, isExit := interrupted.Value().(exitSignal); !isExit {
			return nil, fmt.Errorf("interrupted: %v", interrupted.Value())
		}
		err = nil
	}
	if err != nil {
		return nil, err
	}
	if s.exitCode != 0 {
		return nil, fmt.Errorf("exit status %d", s.exitCode)
	}
	return s.stdout.Bytes(), nil
}

// post hands a callback from another goroutine to the runtime's loop.
func (s *session) post(job func() error) {
	select {
	case s.jobs <- job:
	case <-s.done:
	}
}

// installGlobals defines the host objects the SDK and plugins use.
func (s *session) installGlobals() error {
	registry := require.NewRegistry()
	registry.Enable(s.vm)
	url.Enable(s.vm)
	// Hoster resolvers decode obfuscated sources with Buffer.from(…, "base64").
	buffer.Enable(s.vm)

	module := s.vm.NewObject()
	exports := s.vm.NewObject()
	if err := module.Set("exports", exports); err != nil {
		return err
	}
	timers := s.newTimers()
	globals := map[string]any{
		"setTimeout":   timers.set,
		"clearTimeout": timers.clear,
		"module":       module,
		"exports":      exports,
		"require":      s.require,
		"console":      s.console(),
		"process":      s.process(),
		"Bun":          s.bun(),
		"fetch":        s.fetch,
		"AbortSignal":  s.abortSignal(),
		"WebAssembly":  s.webAssembly.object(),
	}
	for name, value := range globals {
		if err := s.vm.Set(name, value); err != nil {
			return fmt.Errorf("set %s: %w", name, err)
		}
	}
	return nil
}

// require serves the modules bundles leave external: fs (the SDK writes its
// response with fs.writeSync) and the HTML parser, implemented natively.
func (s *session) require(name string) (goja.Value, error) {
	switch name {
	case "fs", "node:fs":
		fs := s.vm.NewObject()
		if err := fs.Set("writeSync", s.writeSync); err != nil {
			return nil, err
		}
		return fs, nil
	case htmlParserModule:
		return s.newHTMLParserModule(), nil
	}
	return nil, fmt.Errorf("module %q is not available in the embedded runtime", name)
}

// writeSync writes to stdout (fd 1) or the log (fd 2). Output after
// process.exit is dropped, as it never reaches a real process's pipe.
func (s *session) writeSync(fd int, data string) int {
	if s.exited {
		return 0
	}
	if fd == 1 {
		s.stdout.WriteString(data)
		return len(data)
	}
	log.Printf("plugin %q: %s", s.pluginName, strings.TrimRight(data, "\n"))
	return len(data)
}

// console logs every level to the backend log.
func (s *session) console() *goja.Object {
	console := s.vm.NewObject()
	logLine := func(call goja.FunctionCall) goja.Value {
		parts := make([]string, len(call.Arguments))
		for i, argument := range call.Arguments {
			parts[i] = argument.String()
		}
		log.Printf("plugin %q: %s", s.pluginName, strings.Join(parts, " "))
		return goja.Undefined()
	}
	for _, level := range []string{"log", "info", "warn", "error", "debug"} {
		_ = console.Set(level, logLine)
	}
	return console
}

// process exposes env and exit; exit stops the runtime at once.
func (s *session) process() *goja.Object {
	process := s.vm.NewObject()
	env := s.vm.NewObject()
	for _, pair := range s.env {
		name, value, found := strings.Cut(pair, "=")
		if found {
			_ = env.Set(name, value)
		}
	}
	_ = process.Set("env", env)
	_ = process.Set("argv", []string{})
	_ = process.Set("exit", func(code int) {
		if s.exited {
			return
		}
		s.exited = true
		s.exitCode = code
		s.vm.Interrupt(exitSignal{})
	})
	return process
}

// bun exposes Bun.stdin.text(), which resolves to the invocation payload.
func (s *session) bun() *goja.Object {
	stdin := s.vm.NewObject()
	_ = stdin.Set("text", func() *goja.Promise {
		promise, resolve, _ := s.vm.NewPromise()
		_ = resolve(s.stdin)
		return promise
	})
	bun := s.vm.NewObject()
	_ = bun.Set("stdin", stdin)
	return bun
}

// abortSignal provides AbortSignal.timeout(ms), the only form plugins use;
// fetch reads the timeout back off the returned signal.
func (s *session) abortSignal() *goja.Object {
	abortSignal := s.vm.NewObject()
	_ = abortSignal.Set("timeout", func(milliseconds int64) *goja.Object {
		signal := s.vm.NewObject()
		_ = signal.Set("aborted", false)
		_ = signal.Set(timeoutProperty, milliseconds)
		return signal
	})
	return abortSignal
}
