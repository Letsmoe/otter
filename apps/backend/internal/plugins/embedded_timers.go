package plugins

import (
	"time"

	"github.com/dop251/goja"
)

// timers implements setTimeout and clearTimeout for one session. Plugins use
// them for retry backoff; a pending timer keeps the session alive like a
// pending fetch does.
type timers struct {
	session *session
	nextID  int64
	// Cancel channels of the timers that haven't fired yet.
	cancels map[int64]chan struct{}
}

func (s *session) newTimers() *timers {
	return &timers{session: s, cancels: map[int64]chan struct{}{}}
}

// set is setTimeout(callback, milliseconds, ...arguments); it returns the timer id.
func (t *timers) set(call goja.FunctionCall) goja.Value {
	callback, isFunction := goja.AssertFunction(call.Argument(0))
	if !isFunction {
		panic(t.session.vm.NewTypeError("setTimeout needs a function"))
	}
	delay := time.Duration(call.Argument(1).ToInteger()) * time.Millisecond
	var arguments []goja.Value
	if len(call.Arguments) > 2 {
		// goja reuses the call's argument slice once setTimeout returns.
		arguments = append(arguments, call.Arguments[2:]...)
	}

	t.nextID++
	timerID := t.nextID
	cancel := make(chan struct{})
	t.cancels[timerID] = cancel
	t.session.pending++
	go t.wait(timerID, delay, cancel, func() error {
		_, err := callback(goja.Undefined(), arguments...)
		return err
	})
	return t.session.vm.ToValue(timerID)
}

// wait sleeps on its own goroutine, then hands the callback to the session
// loop. A cancelled timer still posts an empty job so pending stays balanced.
func (t *timers) wait(timerID int64, delay time.Duration, cancel <-chan struct{}, fire func() error) {
	timer := time.NewTimer(delay)
	defer timer.Stop()
	select {
	case <-timer.C:
		t.session.post(func() error {
			delete(t.cancels, timerID)
			return fire()
		})
	case <-cancel:
		t.session.post(func() error { return nil })
	case <-t.session.done:
	}
}

// clear is clearTimeout(timerID); unknown or already fired ids are ignored.
func (t *timers) clear(timerID int64) {
	cancel, found := t.cancels[timerID]
	if !found {
		return
	}
	delete(t.cancels, timerID)
	close(cancel)
}
