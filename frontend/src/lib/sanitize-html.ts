import DOMPurify from 'isomorphic-dompurify';

type SanitizeOptions = NonNullable<Parameters<typeof DOMPurify.sanitize>[1]>;

const safeUriPattern =
  /^(?:(?:(?:f|ht)tps?|mailto|tel|sms|cid):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i;

const emailAdditionalAttrs = [
  'align',
  'bgcolor',
  'border',
  'cellpadding',
  'cellspacing',
  'height',
  'role',
  'style',
  'target',
  'valign',
  'width',
];

const baseProfile: SanitizeOptions = {
  USE_PROFILES: { html: true },
  ALLOWED_URI_REGEXP: safeUriPattern,
  FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'base', 'meta', 'link'],
  FORBID_ATTR: [
    'onabort',
    'onblur',
    'onchange',
    'onclick',
    'onerror',
    'onfocus',
    'oninput',
    'onkeydown',
    'onkeypress',
    'onkeyup',
    'onload',
    'onmouseover',
    'onreset',
    'onsubmit',
    'srcset',
    'style',
  ],
};

const emailProfile: SanitizeOptions = {
  ...baseProfile,
  ADD_TAGS: ['center'],
  ADD_ATTR: emailAdditionalAttrs,
  FORBID_TAGS: [...(baseProfile.FORBID_TAGS ?? []), 'form', 'input', 'textarea', 'select', 'option', 'button'],
  FORBID_ATTR: baseProfile.FORBID_ATTR?.filter((attr) => attr !== 'style') ?? [],
};

const landingPageProfile: SanitizeOptions = {
  ...emailProfile,
  ADD_TAGS: ['form', 'input', 'textarea', 'select', 'option', 'button'],
  ADD_ATTR: [
    ...emailAdditionalAttrs,
    'autocomplete',
    'checked',
    'disabled',
    'for',
    'method',
    'name',
    'placeholder',
    'readonly',
    'required',
    'selected',
    'type',
    'value',
  ],
  FORBID_TAGS: emailProfile.FORBID_TAGS?.filter(
    (tag) => !['form', 'input', 'textarea', 'select', 'option', 'button'].includes(tag)
  ),
  FORBID_ATTR: [...(emailProfile.FORBID_ATTR ?? []), 'action', 'formaction'],
};

export const sanitizeRichContentHtml = (html?: string | null) =>
  DOMPurify.sanitize(html ?? '', baseProfile);

export const sanitizeEmailHtml = (html?: string | null) =>
  DOMPurify.sanitize(html ?? '', emailProfile);

export const sanitizeLandingPageHtml = (html?: string | null) =>
  DOMPurify.sanitize(html ?? '', landingPageProfile);
