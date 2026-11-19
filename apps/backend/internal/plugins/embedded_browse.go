package plugins

import (
	"errors"

	"github.com/dop251/goja"

	"github.com/Letsmoe/otter/internal/challenge"
)

// browse implements the global __otterBrowse(url) behind the SDK's ctx.browser.open:
// the phone app opens the page in a WebView, passes any captcha on it (shown to the
// user only while they wait to play), and the promise resolves with the first URL off
// the site. It rejects with "blocked: …" where no app can do that.
func (s *session) browse(pageURL string) *goja.Promise {
	promise, resolve, reject := s.vm.NewPromise()
	if s.fetcher.challenges == nil {
		_ = reject(s.vm.NewGoError(errors.New("blocked: no app connected that can open the page")))
		return promise
	}
	s.pending++
	go func() {
		finalURL, err := s.fetcher.challenges.Navigate(s.ctx, pageURL)
		s.post(func() error {
			if err != nil {
				return reject(s.vm.NewGoError(browseError(err)))
			}
			return resolve(finalURL)
		})
	}()
	return promise
}

// browseError starts with the plugin error code the SDK maps it to.
func browseError(err error) error {
	if errors.Is(err, challenge.ErrNoSolver) || errors.Is(err, challenge.ErrUnsolved) {
		return errors.New("blocked: " + err.Error())
	}
	return err
}
