package plugins

import (
	"strings"
	"sync"

	"github.com/andybalholm/cascadia"
	"github.com/dop251/goja"
	"golang.org/x/net/html"
)

// htmlParserModule is the module name plugins import their HTML parser from.
// Bundles keep it external, so under Bun the real node-html-parser runs and
// in the embedded runtime this Go implementation answers instead — parsing
// and selector matching in JS was what made goja slow (see embedded.go).
const htmlParserModule = "node-html-parser"

// selectors caches compiled CSS selectors across invocations; plugins reuse a
// handful of literal selector strings.
var selectors sync.Map // selector string → cascadia.SelectorGroup

// compileSelector parses a (possibly comma-separated) CSS selector, cached.
func compileSelector(selector string) (cascadia.SelectorGroup, error) {
	if cached, ok := selectors.Load(selector); ok {
		return cached.(cascadia.SelectorGroup), nil
	}
	group, err := cascadia.ParseGroup(selector)
	if err != nil {
		return nil, err
	}
	selectors.Store(selector, group)
	return group, nil
}

// htmlParser exposes parsed documents to one session, wrapping each node in a
// JS object once so identity comparisons hold.
type htmlParser struct {
	vm       *goja.Runtime
	elements map[*html.Node]*goja.Object
}

// newHTMLParserModule builds the require("node-html-parser") exports: parse,
// plus an HTMLElement placeholder for code that only uses it as a type.
func (s *session) newHTMLParserModule() *goja.Object {
	parser := &htmlParser{vm: s.vm, elements: map[*html.Node]*goja.Object{}}
	module := s.vm.NewObject()
	_ = module.Set("parse", parser.parse)
	_ = module.Set("HTMLElement", func(goja.ConstructorCall) *goja.Object {
		panic(s.vm.NewTypeError("HTMLElement cannot be constructed in the embedded runtime"))
	})
	return module
}

// parse parses a document or fragment and returns its root element.
func (p *htmlParser) parse(source string) (*goja.Object, error) {
	document, err := html.Parse(strings.NewReader(source))
	if err != nil {
		return nil, err
	}
	return p.wrap(document), nil
}

// wrap returns the JS element for node, creating it on first use.
func (p *htmlParser) wrap(node *html.Node) *goja.Object {
	if object, ok := p.elements[node]; ok {
		return object
	}
	object := p.vm.NewDynamicObject(&element{parser: p, node: node})
	p.elements[node] = object
	return object
}

// wrapOrNull wraps node, or returns null when there is none.
func (p *htmlParser) wrapOrNull(node *html.Node) goja.Value {
	if node == nil {
		return goja.Null()
	}
	return p.wrap(node)
}

// element is node-html-parser's HTMLElement, reduced to what plugins use:
// querySelector(All), getAttribute/hasAttribute, closest, text/rawText,
// innerHTML/outerHTML/toString, tagName, id, classList and parentNode.
type element struct {
	parser *htmlParser
	node   *html.Node
}

// Get resolves a property; methods are created on access.
func (e *element) Get(key string) goja.Value {
	vm := e.parser.vm
	switch key {
	case "querySelector":
		return vm.ToValue(e.querySelector)
	case "querySelectorAll":
		return vm.ToValue(e.querySelectorAll)
	case "getAttribute":
		return vm.ToValue(e.getAttribute)
	case "hasAttribute":
		return vm.ToValue(func(name string) bool {
			_, found := e.attribute(name)
			return found
		})
	case "closest":
		return vm.ToValue(e.closest)
	case "toString":
		return vm.ToValue(func() string { return renderNode(e.node) })
	case "text", "textContent", "innerText", "rawText":
		return vm.ToValue(textContent(e.node))
	case "innerHTML":
		return vm.ToValue(innerHTML(e.node))
	case "outerHTML":
		return vm.ToValue(renderNode(e.node))
	case "tagName":
		return vm.ToValue(strings.ToUpper(e.node.Data))
	case "rawTagName":
		return vm.ToValue(e.node.Data)
	case "id":
		value, _ := e.attribute("id")
		return vm.ToValue(value)
	case "classList":
		return e.classList()
	case "parentNode":
		return e.parser.wrapOrNull(e.node.Parent)
	}
	return nil
}

// Set ignores writes: plugins only read documents.
func (e *element) Set(key string, value goja.Value) bool { return false }

// Has reports the properties Get serves.
func (e *element) Has(key string) bool { return e.Get(key) != nil }

// Delete ignores deletes.
func (e *element) Delete(key string) bool { return false }

// Keys lists no enumerable properties, like node-html-parser's prototype getters.
func (e *element) Keys() []string { return nil }

// selector compiles a selector, throwing a JS TypeError when it is invalid.
func (e *element) selector(selector string) cascadia.SelectorGroup {
	group, err := compileSelector(selector)
	if err != nil {
		panic(e.parser.vm.NewTypeError("invalid selector %q: %v", selector, err))
	}
	return group
}

// querySelector returns the first descendant matching selector, or null.
func (e *element) querySelector(selector string) goja.Value {
	return e.parser.wrapOrNull(cascadia.Query(e.node, e.selector(selector)))
}

// querySelectorAll returns every descendant matching selector, in document order.
func (e *element) querySelectorAll(selector string) *goja.Object {
	nodes := cascadia.QueryAll(e.node, e.selector(selector))
	items := make([]any, len(nodes))
	for i, node := range nodes {
		items[i] = e.parser.wrap(node)
	}
	return e.parser.vm.NewArray(items...)
}

// closest returns the element itself or its nearest ancestor matching selector, or null.
func (e *element) closest(selector string) goja.Value {
	group := e.selector(selector)
	for node := e.node; node != nil; node = node.Parent {
		if node.Type == html.ElementNode && group.Match(node) {
			return e.parser.wrap(node)
		}
	}
	return goja.Null()
}

// getAttribute returns the attribute's (entity-decoded) value, or undefined.
func (e *element) getAttribute(name string) goja.Value {
	value, found := e.attribute(name)
	if !found {
		return goja.Undefined()
	}
	return e.parser.vm.ToValue(value)
}

// attribute looks an attribute up case-insensitively, as HTML does.
func (e *element) attribute(name string) (string, bool) {
	name = strings.ToLower(name)
	for _, attribute := range e.node.Attr {
		if attribute.Key == name {
			return attribute.Val, true
		}
	}
	return "", false
}

// classList supports contains(), length and value.
func (e *element) classList() goja.Value {
	value, _ := e.attribute("class")
	classes := strings.Fields(value)
	list := e.parser.vm.NewObject()
	_ = list.Set("contains", func(name string) bool {
		for _, class := range classes {
			if class == name {
				return true
			}
		}
		return false
	})
	_ = list.Set("length", len(classes))
	_ = list.Set("value", value)
	return list
}

// textContent concatenates every text node below node, script contents included.
func textContent(node *html.Node) string {
	if node.Type == html.TextNode {
		return node.Data
	}
	var text strings.Builder
	var walk func(*html.Node)
	walk = func(current *html.Node) {
		for child := current.FirstChild; child != nil; child = child.NextSibling {
			if child.Type == html.TextNode {
				text.WriteString(child.Data)
				continue
			}
			walk(child)
		}
	}
	walk(node)
	return text.String()
}

// renderNode serializes node and its subtree back to HTML.
func renderNode(node *html.Node) string {
	var out strings.Builder
	_ = html.Render(&out, node)
	return out.String()
}

// innerHTML serializes node's children.
func innerHTML(node *html.Node) string {
	var out strings.Builder
	for child := node.FirstChild; child != nil; child = child.NextSibling {
		_ = html.Render(&out, child)
	}
	return out.String()
}
