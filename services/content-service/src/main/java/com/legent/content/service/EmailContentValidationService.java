package com.legent.content.service;

import com.legent.content.dto.EmailStudioDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailContentValidationService {

    private static final Pattern UNSUPPORTED_TAG_PATTERN = Pattern.compile(
            "(?is)<\\s*/?\\s*(script|iframe|object|embed|form|input|button|select|textarea|video|audio|canvas|base|meta|link|svg|math|amp-[a-z0-9-]+)\\b[^>]*>");
    private static final Pattern EVENT_ATTRIBUTE_PATTERN = Pattern.compile(
            "(?is)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Set<String> DANGEROUS_TAGS = Set.of(
            "script", "iframe", "object", "embed", "video", "audio", "canvas", "base", "meta", "link", "svg", "math");
    private static final Set<String> FORM_TAGS = Set.of("form", "input", "button", "select", "textarea", "option", "label");
    private static final Set<String> COMMON_TAGS = Set.of(
            "a", "abbr", "b", "blockquote", "br", "caption", "cite", "code", "col", "colgroup", "div", "em", "font",
            "h1", "h2", "h3", "h4", "h5", "h6", "hr", "i", "img", "li", "ol", "p", "pre", "s", "small", "span",
            "strong", "sub", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "u", "ul");
    private static final Set<String> COMMON_ATTRIBUTES = Set.of(
            "align", "alt", "aria-label", "bgcolor", "border", "cellpadding", "cellspacing", "class", "colspan",
            "dir", "height", "id", "lang", "role", "rowspan", "style", "target", "title", "valign", "width");
    private static final Set<String> URL_ATTRIBUTES = Set.of("href", "src");
    private static final Set<String> BLOCKED_ATTRIBUTES = Set.of("srcset");
    private static final Pattern LINK_PATTERN = Pattern.compile("(?is)<a\\b[^>]*>");
    private static final Pattern IMG_PATTERN = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern ALT_PATTERN = Pattern.compile("(?is)\\balt\\s*=");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private static final Pattern DYNAMIC_SLOT_PATTERN = Pattern.compile("\\{\\{\\s*dynamic\\.([a-zA-Z0-9_.-]+)\\s*}}");

    public EmailStudioDto.ValidationResponse validate(String htmlContent, String textContent) {
        String html = htmlContent == null ? "" : htmlContent;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> compatibilityWarnings = new ArrayList<>();
        boolean ampDetected = containsAmp(html);

        Matcher unsupportedTagMatcher = UNSUPPORTED_TAG_PATTERN.matcher(html);
        while (unsupportedTagMatcher.find()) {
            String tag = unsupportedTagMatcher.group(1).toLowerCase(Locale.ROOT);
            if (tag.startsWith("amp-")) {
                errors.add("AMP/interactive email content is unsupported: <" + tag + ">");
            } else {
                errors.add("Unsupported email tag detected: <" + tag + ">");
            }
        }
        if (ampDetected) {
            errors.add("AMP for Email is explicitly unsupported for this release.");
        }
        if (EVENT_ATTRIBUTE_PATTERN.matcher(html).find()) {
            errors.add("Inline JavaScript event attributes are not allowed in email HTML.");
        }

        inspectUrls(html, errors, warnings);
        inspectCss(html, errors, compatibilityWarnings);

        int imageCount = 0;
        int imagesMissingAlt = 0;
        Matcher imgMatcher = IMG_PATTERN.matcher(html);
        while (imgMatcher.find()) {
            imageCount++;
            if (!ALT_PATTERN.matcher(imgMatcher.group()).find()) {
                imagesMissingAlt++;
            }
        }
        if (imagesMissingAlt > 0) {
            compatibilityWarnings.add(imagesMissingAlt + " image(s) are missing alt text.");
        }

        Set<String> tokens = extractMatches(TOKEN_PATTERN, html);
        Set<String> dynamicSlots = extractMatches(DYNAMIC_SLOT_PATTERN, html);
        String generatedText = textContent != null && !textContent.isBlank()
                ? textContent
                : generateTextFallback(html);

        EmailStudioDto.ValidationResponse response = new EmailStudioDto.ValidationResponse();
        response.setStatus(errors.isEmpty() ? "PASS" : "FAIL");
        response.setSanitizedHtml(sanitize(html));
        response.setTextContent(generatedText);
        response.setLinkCount(countMatches(LINK_PATTERN, html));
        response.setImageCount(imageCount);
        response.setImagesMissingAlt(imagesMissingAlt);
        response.setTokenKeys(tokens.stream()
                .filter(token -> !token.startsWith("snippet.") && !token.startsWith("dynamic.") && !token.startsWith("block.") && !token.startsWith("brand."))
                .toList());
        response.setDynamicSlots(dynamicSlots.stream().toList());
        response.setErrors(errors);
        response.setWarnings(warnings);
        response.setCompatibilityWarnings(compatibilityWarnings);
        response.setAmpUnsupported(ampDetected);
        return response;
    }

    public String sanitize(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "";
        }
        return sanitizeFragment(htmlContent);
    }

    public String sanitizeLandingPage(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "";
        }
        return sanitizeFragment(htmlContent);
    }

    public String generateTextFallback(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "";
        }
        String withBreaks = htmlContent
                .replaceAll("(?is)<\\s*br\\s*/?>", "\n")
                .replaceAll("(?is)</\\s*(p|div|tr|li|h[1-6])\\s*>", "\n");
        return HTML_TAG_PATTERN.matcher(withBreaks)
                .replaceAll(" ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s+", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void inspectUrls(String html, List<String> errors, List<String> warnings) {
        Document document = parseFragment(html);
        for (Element element : document.body().getAllElements()) {
            for (String attribute : URL_ATTRIBUTES) {
                if (!element.hasAttr(attribute)) {
                    continue;
                }
                String url = element.attr(attribute);
                if (url.isBlank() || url.trim().startsWith("{{")) {
                    continue;
                }
                String normalized = normalizeUrlForPolicy(url);
                if (normalized.startsWith("javascript:") || normalized.startsWith("data:") || normalized.startsWith("vbscript:")) {
                    errors.add("Unsafe URL scheme is not allowed: " + url);
                    continue;
                }
                if (normalized.startsWith("http://")) {
                    warnings.add("Non-HTTPS URL should be avoided in email content: " + url);
                    continue;
                }
                if (!isAllowedUrl(url)) {
                    errors.add("Unsupported URL scheme in email content: " + url);
                }
            }
            if (element.hasAttr("srcset")) {
                errors.add("Responsive image srcset is not allowed in email HTML.");
            }
        }
    }

    private void inspectCss(String html, List<String> errors, List<String> compatibilityWarnings) {
        Document document = parseFragment(html);
        for (Element element : document.body().getAllElements()) {
            if (!element.hasAttr("style")) {
                continue;
            }
            String css = element.attr("style");
            String lower = css.toLowerCase(Locale.ROOT);
            if (containsUnsafeCss(css)) {
                errors.add("Unsafe CSS rule detected in inline styles.");
            }
            if (lower.contains("display:flex") || lower.contains("display: grid") || lower.contains("position:fixed")
                    || lower.contains("position:absolute") || lower.contains("background-image")) {
                compatibilityWarnings.add("CSS may render inconsistently across Outlook/Gmail/Apple Mail: " + compactCss(css));
            }
        }
    }

    private String sanitizeFragment(String htmlContent) {
        Document document = parseFragment(htmlContent);
        sanitizeChildren(document.body());
        return document.body().html();
    }

    private Document parseFragment(String htmlContent) {
        Document document = Jsoup.parseBodyFragment(htmlContent);
        document.outputSettings()
                .prettyPrint(false)
                .syntax(Document.OutputSettings.Syntax.html)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.base);
        return document;
    }

    private void sanitizeChildren(Element parent) {
        for (Element child : new ArrayList<>(parent.children())) {
            String tag = child.normalName();
            if (isDangerousTag(tag) || FORM_TAGS.contains(tag)) {
                child.remove();
                continue;
            }

            sanitizeChildren(child);
            if (!isAllowedTag(tag)) {
                child.unwrap();
                continue;
            }
            sanitizeAttributes(child);
        }
    }

    private boolean isDangerousTag(String tag) {
        return DANGEROUS_TAGS.contains(tag) || tag.startsWith("amp-");
    }

    private boolean isAllowedTag(String tag) {
        return COMMON_TAGS.contains(tag);
    }

    private void sanitizeAttributes(Element element) {
        for (Attribute attribute : new ArrayList<>(element.attributes().asList())) {
            String key = attribute.getKey().toLowerCase(Locale.ROOT);
            if (key.startsWith("on") || BLOCKED_ATTRIBUTES.contains(key) || !isAllowedAttribute(element, key)) {
                element.removeAttr(attribute.getKey());
                continue;
            }
            if (URL_ATTRIBUTES.contains(key) && !isAllowedUrl(attribute.getValue())) {
                element.removeAttr(attribute.getKey());
                continue;
            }
            if ("style".equals(key)) {
                String safeStyle = sanitizeCss(attribute.getValue());
                if (safeStyle.isBlank()) {
                    element.removeAttr(attribute.getKey());
                } else {
                    element.attr(attribute.getKey(), safeStyle);
                }
            }
        }
    }

    private boolean isAllowedAttribute(Element element, String key) {
        String tag = element.normalName();
        if (URL_ATTRIBUTES.contains(key)) {
            return ("href".equals(key) && "a".equals(tag))
                    || ("src".equals(key) && "img".equals(tag));
        }
        return COMMON_ATTRIBUTES.contains(key);
    }

    private String sanitizeCss(String css) {
        List<String> safeRules = new ArrayList<>();
        for (String rawRule : css.split(";")) {
            String rule = rawRule.trim();
            if (rule.isBlank() || containsUnsafeCss(rule)) {
                continue;
            }
            safeRules.add(rule);
        }
        return String.join("; ", safeRules);
    }

    private boolean containsUnsafeCss(String css) {
        String lower = normalizeCssEscapes(Parser.unescapeEntities(css, true)).toLowerCase(Locale.ROOT);
        String compact = lower.replaceAll("[\\u0000-\\u001F\\u007F\\s]+", "");
        return compact.contains("expression(")
                || compact.contains("behavior:")
                || compact.contains("@import")
                || compact.contains("-moz-binding")
                || Arrays.stream(compact.split("url\\(", -1))
                .skip(1)
                .map(part -> part.replaceFirst("^[\"']?", ""))
                .anyMatch(part -> part.startsWith("javascript:") || part.startsWith("data:") || part.startsWith("vbscript:"));
    }

    private String normalizeCssEscapes(String css) {
        StringBuilder normalized = new StringBuilder(css.length());
        for (int i = 0; i < css.length(); i++) {
            char current = css.charAt(i);
            if (current != '\\' || i + 1 >= css.length()) {
                normalized.append(current);
                continue;
            }

            int escapeStart = i + 1;
            int escapeEnd = escapeStart;
            while (escapeEnd < css.length()
                    && escapeEnd - escapeStart < 6
                    && Character.digit(css.charAt(escapeEnd), 16) >= 0) {
                escapeEnd++;
            }

            if (escapeEnd > escapeStart) {
                int codePoint = Integer.parseInt(css.substring(escapeStart, escapeEnd), 16);
                if (Character.isValidCodePoint(codePoint)) {
                    normalized.appendCodePoint(codePoint);
                }
                i = escapeEnd - 1;
                if (i + 1 < css.length() && Character.isWhitespace(css.charAt(i + 1))) {
                    i++;
                }
                continue;
            }

            normalized.append(css.charAt(escapeStart));
            i = escapeStart;
        }
        return normalized.toString();
    }

    private boolean isAllowedUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = Parser.unescapeEntities(url, true).trim();
        if (trimmed.startsWith("{{") || trimmed.startsWith("#")) {
            return true;
        }
        String normalized = normalizeUrlForPolicy(trimmed);
        if (normalized.startsWith("javascript:") || normalized.startsWith("data:") || normalized.startsWith("vbscript:")) {
            return false;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            String lowerScheme = scheme.toLowerCase(Locale.ROOT);
            return lowerScheme.equals("https")
                    || lowerScheme.equals("http")
                    || lowerScheme.equals("mailto")
                    || lowerScheme.equals("tel");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String normalizeUrlForPolicy(String url) {
        return Parser.unescapeEntities(url, true)
                .trim()
                .replaceAll("[\\u0000-\\u001F\\u007F\\s]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean containsAmp(String html) {
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("<html amp")
                || lower.contains("<html ⚡")
                || lower.contains("amp4email")
                || lower.contains("<amp-");
    }

    private Set<String> extractMatches(Pattern pattern, String value) {
        Set<String> matches = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return matches;
    }

    private int countMatches(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String compactCss(String css) {
        return css.replaceAll("\\s+", " ").trim();
    }
}
