"use client";
import { useEffect, useState } from "react";
import { useRouter, useParams } from "next/navigation";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { SegmentRuleBuilder } from "@/components/audience/SegmentRuleBuilder";
import { get, put } from "@/lib/api-client";

export default function EditSegmentPage() {
  const router = useRouter();
  const params = useParams();
  const segmentId = params?.id as string;
  const [form, setForm] = useState({
    name: "",
    description: "",
    rules: {
      operator: "AND",
      conditions: [],
      groups: []
    } as any,
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const fetchSegment = async () => {
      setLoading(true);
      try {
        const segment = await get<{ name?: string; description?: string; rules?: any }>(`/segments/${segmentId}`);
        setForm({
          name: segment?.name || "",
          description: segment?.description || "",
          rules: segment?.rules || { operator: "AND", conditions: [], groups: [] },
        });
      } catch (e) {
        alert("Failed to load segment");
        router.push("/audience/segments");
      }
      setLoading(false);
    };
    if (segmentId) fetchSegment();
  }, [segmentId, router]);

  const handleSave = async () => {
    setSaving(true);
    try {
      await put(`/segments/${segmentId}`, {
        name: form.name,
        description: form.description,
        rules: form.rules,
      });
      router.push("/audience/segments");
    } catch (e) {
      alert("Failed to update segment");
    }
    setSaving(false);
  };

  if (loading) return <div className="p-8 text-center">Loading...</div>;

  return (
    <div className="max-w-2xl mx-auto py-8">
      <Card>
        <h2 className="text-xl font-bold mb-4">Edit Segment</h2>
        <div className="space-y-4">
          <Input
            label="Name *"
            value={form.name}
            onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
          />
          <Input
            label="Description"
            value={form.description}
            onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
          />
          <div>
            <label className="block text-sm font-medium mb-1">Rules *</label>
            <SegmentRuleBuilder initialRules={form.rules} onChange={rules => setForm(f => ({ ...f, rules }))} />
          </div>
          <div className="flex justify-end gap-2 pt-4">
            <Button variant="secondary" onClick={() => router.push("/audience/segments")}>Cancel</Button>
            <Button onClick={handleSave} disabled={!form.name || !form.rules || saving}>Save</Button>
          </div>
        </div>
      </Card>
    </div>
  );
}
