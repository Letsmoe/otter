package challenge

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/Letsmoe/otter/internal/cache"
)

// ErrNoSolver means no app is connected that could solve the check.
var ErrNoSolver = errors.New("no app is connected to solve the site's bot check")

// ErrUnsolved means the app couldn't solve the check in time.
var ErrUnsolved = errors.New("the site's bot check was not solved")

// ErrLinkGone means the page no longer has the link to open: showing it to the user won't help.
var ErrLinkGone = errors.New("the link is not on the page any more")

const (
	// A JS check (and a proof-of-work captcha like Altcha) passes in a hidden WebView within
	// seconds; longer means it wants a click.
	hiddenTimeout = 25 * time.Second
	// The user taps the Turnstile box in the app's sheet.
	interactiveTimeout = 2 * time.Minute
	// After a failed hidden attempt, background requests to the site don't try again for this
	// long: every request of a link search would otherwise wait out its own attempt.
	hiddenRetryAfter = 10 * time.Minute
	// Clearances are renewed in the background once this old, before sites expire them.
	renewAfter   = 12 * time.Hour
	clearanceTTL = 24 * time.Hour
	// A site that needed solving lately: its links race after the others.
	neededTTL = 7 * 24 * time.Hour

	clearancePrefix = "challenge:clearance:"
	neededPrefix    = "challenge:needed:"
)

// What the app is asked to do with a page.
const (
	// KindClearance: pass the check and send back the site's cookies.
	KindClearance = "clearance"
	// KindNavigation: open the page, pass any check, and send back the first URL off the site
	// (e.g. a link page that redirects to the hoster once its captcha is solved).
	KindNavigation = "navigation"
)

// NavigateOptions say what the app does on the page before it watches for the hoster.
type NavigateOptions struct {
	// Click is a CSS selector of the element to click once the page has loaded, e.g. a link's button.
	Click string
	// Submit is a CSS selector of a form to submit as soon as its captchas are solved. Then the
	// first page off the site loading in any frame after the submit counts, not only the whole page.
	Submit string
}

// Clearance is what the app's WebView earned for one site.
type Clearance struct {
	// Cookies is a Cookie header value, e.g. "cf_clearance=…; __cf_bm=…".
	Cookies string `json:"cookies"`
	// UserAgent is the WebView's; the clearance only holds with it.
	UserAgent string    `json:"userAgent"`
	SolvedAt  time.Time `json:"solvedAt"`
}

// Request is a check the app is asked to solve.
type Request struct {
	ID     string
	Kind   string
	Domain string
	URL    string
	// Interactive asks the app to show the page: the hidden attempt failed and the user waits for this link.
	Interactive bool
	// Navigate is what to do on a navigation's page.
	Navigate NavigateOptions
}

// outcome is what the app sent back for one request.
type outcome struct {
	clearance Clearance
	// finalURL is where a navigation ended up.
	finalURL string
}

type pending struct {
	request Request
	key     string
	done    chan struct{}
	result  outcome
	err     error
}

// Service hands checks to the app and keeps the clearances it sends back.
type Service struct {
	cache *cache.Client

	mutex       sync.Mutex
	nextID      int
	pendingByID map[string]*pending
	// One hidden and one shown attempt per site (clearances) or page (navigations) at a time.
	pendingByKey map[string]*pending
	subscribers  map[chan Request]bool
	clearances   map[string]Clearance
	needed       map[string]bool
	hiddenFailed map[string]time.Time
}

// New loads the stored clearances and the sites that needed solving.
func New(ctx context.Context, cacheClient *cache.Client) *Service {
	service := &Service{
		cache:        cacheClient,
		pendingByID:  map[string]*pending{},
		pendingByKey: map[string]*pending{},
		subscribers:  map[chan Request]bool{},
		clearances:   map[string]Clearance{},
		needed:       map[string]bool{},
		hiddenFailed: map[string]time.Time{},
	}
	service.load(ctx)
	return service
}

func (s *Service) load(ctx context.Context) {
	stored, err := s.cache.Entries(ctx, clearancePrefix)
	if err != nil {
		log.Printf("challenge: loading clearances: %v", err)
	}
	for key, value := range stored {
		var clearance Clearance
		if json.Unmarshal([]byte(value), &clearance) == nil {
			s.clearances[strings.TrimPrefix(key, clearancePrefix)] = clearance
		}
	}
	neededSites, err := s.cache.Entries(ctx, neededPrefix)
	if err != nil {
		log.Printf("challenge: loading sites with checks: %v", err)
	}
	for key := range neededSites {
		s.needed[strings.TrimPrefix(key, neededPrefix)] = true
	}
}

// Available reports whether an app is connected that solves checks.
func (s *Service) Available() bool {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	return len(s.subscribers) > 0
}

// Clearance is the stored clearance of a site; a stale one is renewed in the background.
func (s *Service) Clearance(pageURL string) (Clearance, bool) {
	domain := DomainOf(pageURL)
	s.mutex.Lock()
	clearance, found := s.clearances[domain]
	s.mutex.Unlock()
	if found && time.Since(clearance.SolvedAt) > renewAfter {
		go s.renew(pageURL)
	}
	return clearance, found
}

func (s *Service) renew(pageURL string) {
	if _, err := s.attempt(context.Background(), clearanceOf(pageURL), false); err != nil {
		log.Printf("challenge: renewing %s: %v", DomainOf(pageURL), err)
	}
}

// NeedsSolving reports whether a site showed a check lately.
func (s *Service) NeedsSolving(domain string) bool {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	return s.needed[strings.TrimPrefix(strings.ToLower(domain), "www.")]
}

// Solve has the app pass the check on pageURL and keeps the site's cookies:
// hidden first, shown to the user only when that fails and ctx comes from someone playing.
func (s *Service) Solve(ctx context.Context, pageURL string) (Clearance, error) {
	result, err := s.run(ctx, clearanceOf(pageURL))
	return result.clearance, err
}

// Navigate has the app open pageURL, do what options say, pass any check on the way, and
// returns the first URL off the site, e.g. the hoster a captcha-gated link leads to.
func (s *Service) Navigate(ctx context.Context, pageURL string, options NavigateOptions) (string, error) {
	result, err := s.run(ctx, Request{Kind: KindNavigation, URL: pageURL, Domain: DomainOf(pageURL), Navigate: options})
	return result.finalURL, err
}

func clearanceOf(pageURL string) Request {
	return Request{Kind: KindClearance, URL: pageURL, Domain: DomainOf(pageURL)}
}

func (s *Service) run(ctx context.Context, request Request) (outcome, error) {
	s.markNeeded(request.Domain)
	if !s.Available() {
		return outcome{}, ErrNoSolver
	}
	interactive := IsInteractive(ctx)
	// A hidden attempt that failed a moment ago would fail again: go straight to the user, or give up.
	if s.failedHiddenLately(request.Domain) {
		if !interactive {
			return outcome{}, fmt.Errorf("%w: it wanted a click a moment ago", ErrUnsolved)
		}
		return s.attempt(ctx, request, true)
	}
	result, err := s.attempt(ctx, request, false)
	if err == nil || !interactive || errors.Is(err, ErrLinkGone) {
		return result, err
	}
	return s.attempt(ctx, request, true)
}

func (s *Service) failedHiddenLately(domain string) bool {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	failedAt, found := s.hiddenFailed[domain]
	return found && time.Since(failedAt) < hiddenRetryAfter
}

func (s *Service) markNeeded(domain string) {
	s.mutex.Lock()
	known := s.needed[domain]
	s.needed[domain] = true
	s.mutex.Unlock()
	if known {
		return
	}
	if err := s.cache.Set(context.Background(), neededPrefix+domain, "1", neededTTL); err != nil {
		log.Printf("challenge: remembering %s: %v", domain, err)
	}
}

func (s *Service) attempt(ctx context.Context, request Request, interactive bool) (outcome, error) {
	attempt := s.enqueue(request, interactive)
	select {
	case <-attempt.done:
		return attempt.result, attempt.err
	case <-ctx.Done():
		return outcome{}, ctx.Err()
	}
}

// enqueue joins the attempt already running for the site (or link), or starts one and tells the app.
func (s *Service) enqueue(request Request, interactive bool) *pending {
	request.Interactive = interactive
	key := attemptKey(request)
	s.mutex.Lock()
	defer s.mutex.Unlock()
	if existing, found := s.pendingByKey[key]; found {
		return existing
	}
	s.nextID++
	request.ID = strconv.Itoa(s.nextID)
	attempt := &pending{request: request, key: key, done: make(chan struct{})}
	s.pendingByID[request.ID] = attempt
	s.pendingByKey[key] = attempt
	time.AfterFunc(timeoutOf(interactive), func() { s.finish(request.ID, outcome{}, ErrUnsolved) })
	s.broadcast(request)
	return attempt
}

// A clearance is the site's, a navigation the link's: each one leads somewhere else.
func attemptKey(request Request) string {
	target := request.Domain
	if request.Kind == KindNavigation {
		target = request.URL + "|" + request.Navigate.Click
	}
	return request.Kind + "|" + target + "|" + strconv.FormatBool(request.Interactive)
}

func timeoutOf(interactive bool) time.Duration {
	if interactive {
		return interactiveTimeout
	}
	return hiddenTimeout
}

// broadcast tells every connected app; callers hold s.mutex.
func (s *Service) broadcast(request Request) {
	for subscriber := range s.subscribers {
		s.sendTo(subscriber, request)
	}
}

// sendTo never blocks: an app that stopped reading misses the request, which times out.
func (s *Service) sendTo(subscriber chan Request, request Request) {
	select {
	case subscriber <- request:
	default:
		log.Printf("challenge: an app is not keeping up; %s not sent", request.Domain)
	}
}

// Complete takes what the app earned: the site's cookies and, for a navigation, where it led.
func (s *Service) Complete(id, cookies, userAgent, finalURL string) error {
	s.mutex.Lock()
	attempt, found := s.pendingByID[id]
	s.mutex.Unlock()
	if !found {
		return fmt.Errorf("challenge %s is not pending", id)
	}
	if attempt.request.Kind == KindNavigation && finalURL == "" {
		return fmt.Errorf("challenge %s is a navigation and needs the URL it led to", id)
	}
	clearance := Clearance{Cookies: cookies, UserAgent: userAgent, SolvedAt: time.Now()}
	if cookies != "" {
		s.store(attempt.request.Domain, clearance)
	}
	s.finish(id, outcome{clearance: clearance, finalURL: finalURL}, nil)
	return nil
}

func (s *Service) store(domain string, clearance Clearance) {
	s.mutex.Lock()
	s.clearances[domain] = clearance
	s.mutex.Unlock()
	encoded, _ := json.Marshal(clearance)
	if err := s.cache.Set(context.Background(), clearancePrefix+domain, string(encoded), clearanceTTL); err != nil {
		log.Printf("challenge: storing the clearance of %s: %v", domain, err)
	}
}

// Fail ends an attempt the app gave up on, e.g. because the page wants a click. linkGone
// says the page doesn't have what to click: the link is dead, and nobody is asked to try.
func (s *Service) Fail(id, reason string, linkGone bool) {
	if linkGone {
		s.finish(id, outcome{}, fmt.Errorf("%w: %s", ErrLinkGone, reason))
		return
	}
	s.finish(id, outcome{}, fmt.Errorf("%w: %s", ErrUnsolved, reason))
}

func (s *Service) finish(id string, result outcome, err error) {
	s.mutex.Lock()
	defer s.mutex.Unlock()
	attempt, found := s.pendingByID[id]
	if !found {
		return
	}
	delete(s.pendingByID, id)
	delete(s.pendingByKey, attempt.key)
	if err != nil && !attempt.request.Interactive && !errors.Is(err, ErrLinkGone) {
		s.hiddenFailed[attempt.request.Domain] = time.Now()
	}
	if err == nil {
		delete(s.hiddenFailed, attempt.request.Domain)
	}
	attempt.result = result
	attempt.err = err
	close(attempt.done)
}

// Subscribe receives every check to solve until ctx ends, starting with the ones already waiting.
func (s *Service) Subscribe(ctx context.Context) <-chan Request {
	requests := make(chan Request, 16)
	s.mutex.Lock()
	s.subscribers[requests] = true
	for _, attempt := range s.pendingByID {
		s.sendTo(requests, attempt.request)
	}
	s.mutex.Unlock()
	go func() {
		<-ctx.Done()
		s.mutex.Lock()
		delete(s.subscribers, requests)
		s.mutex.Unlock()
		close(requests)
	}()
	return requests
}
