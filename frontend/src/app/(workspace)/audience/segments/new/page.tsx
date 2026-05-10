"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { PageHeader } from "@/components/ui/PageChrome";
import { SegmentRuleBuilder } from "@/components/audience/SegmentRuleBuilder";
import { post } from "@/lib/api-client";

export default function NewSegmentPage() {
  const router = useRouter();
  const [form, setForm] = useState({
    name: "",
    description: "",
    rules: {
      operator: "AND",
      conditions: [],
      groups: []
    } as any,
  });
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    try {
      await post("/segments", {
        name: form.name,
        description: form.description,
        rules: form.rules,
      });
      router.push("/app/audience/segments");
    } catch (e) {
      alert("Failed to create segment");
    }
    setSaving(false);
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <PageHeader
        eyebrow="Audience rules"
        title="Create Segment"
        description="Define a reusable subscriber group with governed rule logic."
      />
      <Card>
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
            <Button variant="secondary" onClick={() => router.push("/app/audience/segments")}>Cancel</Button>
            <Button onClick={handleSave} disabled={!form.name || !form.rules || saving}>Save</Button>
          </div>
        </div>
      </Card>
    </div>
  );
}
