import { expect, test } from '@playwright/test';
import {
  sanitizeEmailHtml,
  sanitizeLandingPageHtml,
  sanitizeRichContentHtml,
} from '../../src/lib/sanitize-html';

test.describe('HTML sanitization regressions', () => {
  test('removes encoded javascript URLs from rich content', () => {
    const sanitized = sanitizeRichContentHtml('<a href="&#x6a;avascript:alert(1)">Open</a>');

    expect(sanitized).toContain('<a>Open</a>');
    expect(sanitized).not.toContain('javascript:');
    expect(sanitized).not.toContain('href=');
  });

  test('removes srcset attributes from email HTML', () => {
    const sanitized = sanitizeEmailHtml(
      '<img src="https://cdn.example.com/safe.png" srcset="javascript:alert(1) 1x, https://cdn.example.com/safe-2x.png 2x">'
    );

    expect(sanitized).toContain('src="https://cdn.example.com/safe.png"');
    expect(sanitized).not.toContain('srcset');
    expect(sanitized).not.toContain('javascript:');
  });

  test('removes event handlers from email HTML', () => {
    const sanitized = sanitizeEmailHtml('<table onmouseover="alert(1)"><tr><td onclick="alert(2)">Cell</td></tr></table>');

    expect(sanitized).toContain('<table>');
    expect(sanitized).toContain('<td>Cell</td>');
    expect(sanitized).not.toContain('onmouseover');
    expect(sanitized).not.toContain('onclick');
  });

  test('strips landing page action and formaction attributes', () => {
    const sanitized = sanitizeLandingPageHtml(
      '<form action="https://evil.example/collect"><button formaction="javascript:alert(1)" type="submit">Send</button></form>'
    );

    expect(sanitized).toContain('<form>');
    expect(sanitized).toContain('<button type="submit">Send</button>');
    expect(sanitized).not.toContain('action=');
    expect(sanitized).not.toContain('formaction');
    expect(sanitized).not.toContain('javascript:');
  });
});
