import { BlogPostView } from '@/components/marketing/BlogViews';
import { blogPosts } from '@/lib/marketing-data';
import type { Metadata } from 'next';

export async function generateStaticParams() {
  return blogPosts.map((post) => ({ slug: post.slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const resolved = await params;
  const post = blogPosts.find((item) => item.slug === resolved.slug);
  return {
    title: post ? `${post.title} | Legent` : 'Article | Legent',
    description: post?.summary ?? 'Legent public field note.',
  };
}

export default async function BlogPostPage({ params }: { params: Promise<{ slug: string }> }) {
  const resolved = await params;
  return <BlogPostView slug={resolved.slug} />;
}
