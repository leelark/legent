package com.legent.content.service;

import com.legent.content.dto.EmailStudioDto;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailContentValidationService {

    private static final Pattern UNSUPPORTED_TAG_PATTERN = Pattern.compile(
            "(?is)<\\s*/?\\s*(script|iframe|object|embed|form|input|button|select|textarea|video|audio|canvas|base|meta|link|amp-[a-z0-9-]+)\\b[^>]*>");
    private static final Pattern EVENT_ATTRIBUTE_PATTERN = Pattern.compile(
            "(?is)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern LANDING_UNSAFE_TAG_PATTERN = Pattern.compile(
            "(?is)<\\s*/?\\s*(script|iframe|object|embed|base|meta|link|amp-[a-z0-9-]+)\\b[^>]*>");
    private static final Pattern STYLE_ATTRIBUTE_PATTERN = Pattern.compile(
            "(?is)\\sstyle\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");
    private static final Pattern HREF_PATTERN = Pattern.compile(
            "(?is)\\b(?:href|src)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");
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
        String sanitized = UNSUPPORTED_TAG_PATTERN.matcher(htmlContent).replaceAll("");
        sanitized = EVENT_ATTRIBUTE_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = sanitizeStyles(sanitized);
        sanitized = HREF_PATTERN.matcher(sanitized).replaceAll(matchResult -> {
            String quote = matchResult.group(1).startsWith("\"") ? "\"" : "'";
            String url = matchResult.group(2) != null ? matchResult.group(2) : matchResult.group(3);
            return isAllowedUrl(url) ? matchResult.group() : "";
        });
        return sanitized;
    }

    public String sanitizeLandingPage(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "";
        }
        String sanitized = LANDING_UNSAFE_TAG_PATTERN.matcher(htmlContent).replaceAll("");
        sanitized = EVENT_ATTRIBUTE_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = sanitizeStyles(sanitized);
        sanitized = HREF_PATTERN.matcher(sanitized).replaceAll(matchResult -> {
            String url = matchResult.group(2) != null ? matchResult.group(2) : matchResult.group(3);
            return isAllowedUrl(url) ? matchResult.group() : "";
        });
        return sanitized;
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
        Matcher matcher = HREF_PATTERN.matcher(html);
        while (matcher.find()) {
            String url = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (url == null || url.isBlank() || url.trim().startsWith("{{")) {
                continue;
            }
            String normalized = url.trim().toLowerCase(Locale.ROOT);
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
    }

    private void inspectCss(String html, List<String> errors, List<String> compatibilityWarnings) {
        Matcher matcher = STYLE_ATTRIBUTE_PATTERN.matcher(html);
        while (matcher.find()) {
            String css = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            String lower = css.toLowerCase(Locale.ROOT);
            if (lower.contains("expression(") || lower.contains("behavior:") || lower.contains("@import")
                    || lower.contains("-moz-binding") || lower.contains("url(javascript:")) {
                errors.add("Unsafe CSS rule detected in inline styles.");
            }
            if (lower.contains("display:flex") || lower.contains("display: grid") || lower.contains("position:fixed")
                    || lower.contains("position:absolute") || lower.contains("background-image")) {
                compatibilityWarnings.add("CSS may render inconsistently across Outlook/Gmail/Apple Mail: " + compactCss(css));
            }
        }
    }

    private String sanitizeStyles(String html) {
        return STYLE_ATTRIBUTE_PATTERN.matcher(html).replaceAll(matchResult -> {
            String quote = matchResult.group(1).startsWith("\"") ? "\"" : "'";
            String css = matchResult.group(2) != null ? matchResult.group(2) : matchResult.group(3);
            List<String> safeRules = new ArrayList<>();
            for (String rawRule : css.split(";")) {
                String rule = rawRule.trim();
                String lower = rule.toLowerCase(Locale.ROOT);
                if (rule.isBlank()
                        || lower.contains("expression(")
                        || lower.contains("behavior:")
                        || lower.contains("@import")
                        || lower.contains("-moz-binding")
                        || lower.contains("url(javascript:")
                        || lower.contains("url(data:")) {
                    continue;
                }
                safeRules.add(rule);
            }
            return safeRules.isEmpty() ? "" : " style=" + quote + String.join("; ", safeRules) + quote;
        });
    }

    private boolean isAllowedUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("{{") || trimmed.startsWith("#")) {
            return true;
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
