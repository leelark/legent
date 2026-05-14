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

  const landingFormTargetVectors = [
    {
      name: 'external targets',
      action: 'https://evil.example/collect',
      formaction: 'https://evil.example/override',
      blocked: 'evil.example',
    },
    {
      name: 'relative targets',
      action: '/__blocked-landing-form-submit__',
      formaction: '../override',
      blocked: '/__blocked-landing-form-submit__',
    },
    {
      name: 'protocol-relative targets',
      action: '//evil.example/collect',
      formaction: '//evil.example/override',
      blocked: '//evil.example',
    },
    {
      name: 'encoded unsafe targets',
      action: '&#x6a;avascript:alert(1)',
      formaction: 'java&#x73;cript:alert(2)',
      blocked: 'javascript:',
    },
  ];

  for (const vector of landingFormTargetVectors) {
    test(`keeps landing forms inert by unwrapping ${vector.name}`, () => {
      const sanitized = sanitizeLandingPageHtml(
        `<form action="${vector.action}" method="post"><button formaction="${vector.formaction}" type="submit">Send</button></form>`
      );

      expect(sanitized).toContain('<button type="submit">Send</button>');
      expect(sanitized).not.toContain('<form');
      expect(sanitized).not.toContain('action=');
      expect(sanitized).not.toContain('formaction');
      expect(sanitized.toLowerCase()).not.toContain(vector.blocked.toLowerCase());
    });
  }

  test('does not add a default landing page form target', () => {
    const sanitized = sanitizeLandingPageHtml('<form method="post"><input name="email"></form>');

    expect(sanitized).toContain('<input name="email">');
    expect(sanitized).not.toContain('<form');
    expect(sanitized).not.toContain('action=');
  });
});
