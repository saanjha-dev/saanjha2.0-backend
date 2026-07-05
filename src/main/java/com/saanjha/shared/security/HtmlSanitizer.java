package com.saanjha.shared.security;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Shared XSS defense for any user-authored rich text (Spec H.2 #4: "XSS in
 * Project Markdown ... Sanitized on read AND write via OWASP Java HTML
 * Sanitizer"). This did not exist anywhere in the codebase — identified as a
 * missing piece of shared infrastructure and implemented centrally here so
 * every module with free-text content (Project descriptions today; Task
 * descriptions, Chat messages, etc. tomorrow) reuses the same policy instead
 * of each hand-rolling its own sanitization.
 *
 * The policy permits a conservative set of Markdown-adjacent formatting tags
 * and strips everything else — scripts, event handler attributes, iframes,
 * forms, and unknown elements are all removed rather than escaped, matching
 * the "sanitize" (not merely "encode") requirement in the spec.
 */
public final class HtmlSanitizer {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "b", "i", "em", "strong", "ul", "ol", "li",
                    "code", "pre", "blockquote", "h1", "h2", "h3", "a")
            .allowAttributes("href").onElements("a")
            .allowUrlProtocols("https", "http")
            .requireRelNofollowOnLinks()
            .toFactory();

    private HtmlSanitizer() {
    }

    public static String sanitize(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        return POLICY.sanitize(rawInput);
    }
}
