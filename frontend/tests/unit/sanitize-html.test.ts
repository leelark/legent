import { describe, expect, it } from 'vitest';
import {
  sanitizeEmailHtml,
  sanitizeLandingPageHtml,
  sanitizeRichContentHtml,
} from '../../src/lib/sanitize-html';

describe('sanitize html helpers', () => {
  it('removes executable tags, event handlers, and unsafe urls from rich content', () => {
    const sanitized = sanitizeRichContentHtml(
      '<p onclick="alert(1)">Hi</p><script>alert(2)</script><a href="javascript:alert(3)">bad</a><iframe src="https://evil.example"></iframe>'
    );

    expect(sanitized).toContain('<p>Hi</p>');
    expect(sanitized).not.toContain('onclick');
    expect(sanitized).not.toContain('<script');
    expect(sanitized).not.toContain('<iframe');
    expect(sanitized).not.toContain('javascript:');
  });

  it('keeps email table markup and adds safe rel tokens to blank links', () => {
    const sanitized = sanitizeEmailHtml(
      '<table cellpadding="4" style="color:red"><tr><td><a href="https://example.com" target="_blank" rel="nofollow">Open</a></td></tr></table>'
    );

    expect(sanitized).toContain('<table');
    expect(sanitized).toContain('cellpadding="4"');
    expect(sanitized).toContain('style="color:red"');
    expect(sanitized).toContain('target="_blank"');
    expect(sanitized).toContain('nofollow');
    expect(sanitized).toContain('noopener');
    expect(sanitized).toContain('noreferrer');
  });

  it('blocks form submission attributes for landing pages', () => {
    const sanitized = sanitizeLandingPageHtml(
      '<form action="https://evil.example"><input name="email"><a href="mailto:test@example.com">Mail</a></form>'
    );

    expect(sanitized).not.toContain('<form');
    expect(sanitized).not.toContain('<input');
    expect(sanitized).not.toContain('action=');
    expect(sanitized).toContain('mailto:test@example.com');
  });
});
