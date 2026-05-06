import { BlogPostView } from '@/components/marketing/BlogViews';

export default async function BlogPostPage({ params }: { params: Promise<{ slug: string }> }) {
  const resolved = await params;
  return <BlogPostView slug={resolved.slug} />;
}
