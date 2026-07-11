package com.saanjha.modules.notification.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateEngineTest {

    private final TemplateEngine engine = new TemplateEngine();

    @Test
    void substitutesKnownVariables() {
        String result = engine.render("Hello {{name}}, welcome to {{project}}!", Map.of("name", "Ava", "project", "Saanjha"), false);
        assertThat(result).isEqualTo("Hello Ava, welcome to Saanjha!");
    }

    @Test
    void missingVariable_rendersAsEmptyStringNotLiteralPlaceholder() {
        String result = engine.render("Reason: {{reason}}", Map.of(), false);
        assertThat(result).isEqualTo("Reason: ");
    }

    @Test
    void htmlEscape_neutralizesInjectedMarkup() {
        String result = engine.render("Reason: {{reason}}", Map.of("reason", "<script>alert(1)</script>"), true);
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("&lt;script&gt;");
    }

    @Test
    void plainTextChannel_doesNotEscape() {
        String result = engine.render("Reason: {{reason}}", Map.of("reason", "A & B"), false);
        assertThat(result).isEqualTo("Reason: A & B");
    }

    @Test
    void nullTemplate_returnsNull() {
        assertThat(engine.render(null, Map.of(), false)).isNull();
    }
}
