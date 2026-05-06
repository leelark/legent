import { get, post, put } from '@/lib/api-client';

export type PublicContentRecord = {
  id?: string;
  tenantId?: string | null;
  workspaceId?: string | null;
  contentType: 'PAGE' | 'BLOG' | 'PRICING' | string;
  pageKey: string;
  slug?: string | null;
  title?: string | null;
  status?: string;
  payload?: Record<string, unknown>;
  seoMeta?: Record<string, unknown>;
  publishedAt?: string | null;
  updatedAt?: string | null;
};

export const getPublicPageContent = async (pageKey: string) =>
  get<PublicContentRecord>(`/public/content/${encodeURIComponent(pageKey)}`);

export const getPublicPricingContent = async () =>
  get<PublicContentRecord>('/public/pricing');

export const listPublicBlogPosts = async () =>
  get<PublicContentRecord[]>('/public/blog');

export const getPublicBlogPost = async (slug: string) =>
  get<PublicContentRecord>(`/public/blog/${encodeURIComponent(slug)}`);

export const listAdminPublicContent = async () =>
  get<PublicContentRecord[]>('/admin/public-content');

export const createAdminPublicContent = async (payload: {
  contentType: string;
  pageKey: string;
  slug?: string;
  title?: string;
  status?: string;
  payload?: Record<string, unknown>;
  seoMeta?: Record<string, unknown>;
}) => post<PublicContentRecord>('/admin/public-content', payload);

export const updateAdminPublicContent = async (
  id: string,
  payload: {
    contentType: string;
    pageKey: string;
    slug?: string;
    title?: string;
    status?: string;
    payload?: Record<string, unknown>;
    seoMeta?: Record<string, unknown>;
  }
) => put<PublicContentRecord>(`/admin/public-content/${id}`, payload);

export const publishAdminPublicContent = async (id: string, value = true) =>
  post<PublicContentRecord>(`/admin/public-content/${id}/publish?value=${value}`);

