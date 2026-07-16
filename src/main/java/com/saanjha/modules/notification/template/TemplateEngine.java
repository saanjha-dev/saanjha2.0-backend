package com.saanjha.modules.notification.template;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deliberately NOT a full templating engine (no conditionals, loops, or
 * partials/inheritance) - just {@code {{variable}}} substitution against a
 * flat variable map. This module's template needs (module brief's "Template
 * Engine" section) are: variables, versioning, rendering, validation - all
 * satisfied by this plus  own versioning and
 * {@link TemplateService}'s validation. Adding a real engine (Freemarker,
 * Thymeleaf, Mustache) was considered and declined for v1: none of this
 * codebase's other modules pull in a templating library, and every seeded
 * template in this module is a single flat sentence with 2-4 variables -
 * pulling in a new dependency for that would be solving a problem this
 * module doesn't have yet. Documented as a Future Extension Point for when
 * "template inheritance"/"rich HTML branding" (also brief items) become real
 * requirements rather than speculative ones.
 * <p>
 * That "rich HTML branding" requirement did land (the enterprise email
 * design system redesign), and was deliberately satisfied WITHOUT adding
 * inheritance/partials here: {@code scripts/notification/gen_templates.py}
 * defines the shared layout once and stamps out each EMAIL row as a
 * complete, self-contained HTML document at migration-authoring time, so
 * every row this engine reads is still just static markup plus flat
 * {@code {{variable}}} tokens - exactly what this engine already handles.
 * See {@code docs/notification/email-design-system.md} for why that
 * generator-based approach was chosen over teaching this class to support
 * includes.
 */
@Component
public class TemplateEngine {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*}}");

    /** {@code htmlEscape} should be true for EMAIL/WEBHOOK bodies rendered as HTML, false for plain-text channels (SMS/PUSH/IN_APP). */
    public String render(String template, Map<String, Object> variables, boolean htmlEscape) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            String replacement = value != null ? value.toString() : "";
            if (htmlEscape) {
                replacement = HtmlUtils.htmlEscape(replacement);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
