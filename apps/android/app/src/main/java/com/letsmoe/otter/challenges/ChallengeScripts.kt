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
 * Hides everything but the captcha (its form, else the widget) and pins it at the top, so the
 * sheet shows only what to tap instead of the whole site: "isolated", or "waiting" until a captcha is there.
 * Transforms are dropped, or a modal's would move the pinned box off screen.
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
      'html, body { overflow: hidden !important; background: #10131a !important; }' +
      'body * { visibility: hidden !important; transform: none !important; }' +
      '[data-otter-show], [data-otter-show] * { visibility: visible !important; }' +
      '[data-otter-show] { position: fixed !important; top: 12px !important; left: 12px !important; right: 12px !important;' +
      ' margin: 0 !important; z-index: 2147483647 !important; }';
    document.head.appendChild(style);
  }
  target.setAttribute('data-otter-show', '');
  return 'isolated';
})('$CAPTCHA_WIDGETS')
"""
