import { BlogIndexView } from '@/components/marketing/BlogViews';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Blog | Legent',
  description: 'Field notes for premium email operations, delivery, automation, and lifecycle workflow design.',
};

export default function BlogPage() {
  return <BlogIndexView />;
}
