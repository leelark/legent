import { NextRequest, NextResponse } from 'next/server';

const LEGACY_PREFIXES = [
  '/email',
  '/audience',
  '/campaigns',
  '/automation',
  '/automations',
  '/tracking',
  '/deliverability',
  '/analytics',
  '/admin',
  '/settings',
];

export function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  if (pathname.startsWith('/app') || pathname.startsWith('/api') || pathname.startsWith('/_next')) {
    return NextResponse.next();
  }

  const matched = LEGACY_PREFIXES.find((prefix) => pathname === prefix || pathname.startsWith(prefix + '/'));
  if (!matched) {
    return NextResponse.next();
  }

  const url = request.nextUrl.clone();
  url.pathname = `/app${pathname}`;
  url.search = search;
  return NextResponse.redirect(url, 308);
}

export const config = {
  matcher: ['/((?!.*\\..*).*)'],
};

