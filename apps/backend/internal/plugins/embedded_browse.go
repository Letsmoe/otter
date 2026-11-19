package plugins

import (
	"errors"

	"github.com/dop251/goja"

	"github.com/Letsmoe/otter/internal/challenge"
)

// browse implements the global __otterBrowse(url, { click, submit }) behind the SDK's
// ctx.browser.open: the phone app opens the page in a WebView, clicks and submits what
// the options name, passes any captcha on the way (shown to the user only while they
// wait to play), and the promise resolves with the first URL off the site. It rejects
// with "blocked: …" where no app can do that and "dead_link: …" when the page lacks
// the element to click.
func (s *session) browse(pageURL string, options goja.Value) *goja.Promise {
	promise, resolve, reject := s.vm.NewPromise()
	if s.fetcher.challenges == nil {
		_ = reject(s.vm.NewGoError(errors.New("blocked: no app connected that can open the page")))
		return promise
	}
	navigate := s.navigateOptions(options)
	s.pending++
	go func() {
		finalURL, err := s.fetcher.challenges.Navigate(s.ctx, pageURL, navigate)
		s.post(func() error {
			if err != nil {
				return reject(s.vm.NewGoError(browseError(err)))
			}
			return resolve(finalURL)
		})
	}()
	return promise
}

func (s *session) navigateOptions(options goja.Value) challenge.NavigateOptions {
	if !isSet(options) {
		return challenge.NavigateOptions{}
	}
	object := options.ToObject(s.vm)
	return challenge.NavigateOptions{Click: stringOption(object, "click"), Submit: stringOption(object, "submit")}
}

func stringOption(object *goja.Object, name string) string {
	value := object.Get(name)
	if !isSet(value) {
		return ""
	}
	return value.String()
}

// browseError starts with the plugin error code the SDK maps it to.
func browseError(err error) error {
	if errors.Is(err, challenge.ErrLinkGone) {
		return errors.New("dead_link: " + err.Error())
	}
	if errors.Is(err, challenge.ErrNoSolver) || errors.Is(err, challenge.ErrUnsolved) {
		return errors.New("blocked: " + err.Error())
	}
	return err
}
