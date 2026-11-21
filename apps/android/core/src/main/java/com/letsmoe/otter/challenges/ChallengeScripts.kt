package com.letsmoe.otter.challenges

import org.json.JSONObject

// JavaScript a ChallengePage runs in its page. Each returns a short status string.

// Elements only Cloudflare's check pages have; normal pages may load its scripts too.
const val SHOWS_CHECK_SCRIPT = """
(function() {
  var selectors = '#challenge-form, #challenge-running, #challenge-stage, #cf-challenge-running, .cf-turnstile, #turnstile-wrapper';
  return document.querySelector(selectors) !== null;
})()
"""

// What a captcha widget leaves in its form once solved: Turnstile, Altcha, reCAPTCHA, hCaptcha.
private const val CAPTCHA_INPUTS = "[name=\"cf-turnstile-response\"], [name=\"altcha\"], [name=\"g-recaptcha-response\"], [name=\"h-captcha-response\"]"

// The widgets themselves, to find what the user has to see.
private const val CAPTCHA_WIDGETS = "$CAPTCHA_INPUTS, .cf-turnstile, altcha-widget, iframe[src*=\"challenges.cloudflare.com\"]"

/** Clicks the element [selector] names: "clicked", or "missing" while the page doesn't have it. */
fun clickScript(selector: String): String {
    return """
(function(selector) {
  var element = document.querySelector(selector);
  if (!element) return 'missing';
  element.click();
  return 'clicked';
})(${JSONObject.quote(selector)})
"""
}

/**
 * Submits the form [selector] names once every shown captcha in it is solved (a widget inside
 * something hidden, like a fallback captcha, doesn't count): "submitted", "waiting" or "missing".
 */
fun submitWhenSolvedScript(selector: String): String {
    return """
(function(selector, captchaInputs) {
  var form = document.querySelector(selector);
  if (!form) return 'missing';
  function shown(element) {
    for (var node = element.parentElement; node; node = node.parentElement) {
      if (getComputedStyle(node).display === 'none') return false;
    }
    return true;
  }
  var inputs = Array.prototype.filter.call(form.querySelectorAll(captchaInputs), shown);
  if (inputs.length === 0) return 'waiting';
  for (var index = 0; index < inputs.length; index++) {
    if (!inputs[index].value) return 'waiting';
  }
  if (form.requestSubmit) form.requestSubmit(); else form.submit();
  return 'submitted';
})(${JSONObject.quote(selector)}, ${JSONObject.quote(CAPTCHA_INPUTS)})
"""
}

/**
 * Removes everything but the captcha (its form, else the widget) from the layout, so the sheet
 * shows only what to tap instead of the whole site: "isolated", or "waiting" until a captcha is there.
 * The captcha's ancestors go back into the normal flow (a modal's fixed position or transform would
 * move it off screen), and the page stays scrollable when the captcha is taller than the sheet.
 * Popups a captcha adds later (image challenges) are new body children, so they stay visible.
 */
const val ISOLATE_CAPTCHA_SCRIPT = """
(function(widgets) {
  var marker = document.querySelector(widgets);
  if (!marker) return 'waiting';
  var target = marker.closest('form') || marker.parentElement || marker;
  if (!document.getElementById('otter-isolate')) {
    var style = document.createElement('style');
    style.id = 'otter-isolate';
    style.textContent =
      'html, body { overflow: auto !important; height: auto !important; background: #10131a !important; }' +
      '[data-otter-hide] { display: none !important; }' +
      '[data-otter-path] { position: static !important; transform: none !important; margin: 0 !important;' +
      ' padding: 0 !important; width: auto !important; height: auto !important; min-height: 0 !important;' +
      ' max-height: none !important; overflow: visible !important; background: transparent !important; }' +
      '[data-otter-show] { margin: 12px !important; }';
    document.head.appendChild(style);
  }
  function hideSiblingsOf(node) {
    var siblings = node.parentElement.children;
    for (var index = 0; index < siblings.length; index++) {
      if (siblings[index] !== node) siblings[index].setAttribute('data-otter-hide', '');
    }
  }
  for (var node = target; node.parentElement && node !== document.body; node = node.parentElement) {
    hideSiblingsOf(node);
    if (node !== target) node.setAttribute('data-otter-path', '');
  }
  target.setAttribute('data-otter-show', '');
  return 'isolated';
})('$CAPTCHA_WIDGETS')
"""
