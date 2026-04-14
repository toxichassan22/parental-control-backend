import { useEffect, useState } from "react";
import { upsertPolicy } from "../firebase";
import type { DeviceRecord, PolicyRecord } from "../types";

interface PolicyEditorProps {
  device: DeviceRecord;
  policy: PolicyRecord | null;
}

export function PolicyEditor({ device, policy }: PolicyEditorProps) {
  const [dailyLimitGb, setDailyLimitGb] = useState("2");
  const [allowlistText, setAllowlistText] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!policy) {
      return;
    }

    setDailyLimitGb(String(policy.dailyLimitBytes / 1024 / 1024 / 1024));
    setAllowlistText(policy.allowlistPackages.join("\n"));
  }, [policy]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setMessage(null);

    try {
      const parsedLimit = Number.parseFloat(dailyLimitGb);
      await upsertPolicy({
        deviceId: device.id,
        policy: {
          dailyLimitBytes: Math.max(0, Number.isFinite(parsedLimit) ? parsedLimit : 0) * 1024 * 1024 * 1024,
          allowlistPackages: allowlistText
            .split("\n")
            .map((item) => item.trim())
            .filter(Boolean),
          enforcementEnabled: true,
          adminTimezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
          resetMode: "SERVER_WINDOW",
        },
      });
      setMessage("تم حفظ السياسة الجديدة");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "تعذر حفظ السياسة");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Policy</p>
          <h3>سياسة الجهاز</h3>
        </div>
        <span className="metric-pill">v{policy?.policyVersion ?? 0}</span>
      </div>

      <form className="form-grid" onSubmit={handleSubmit}>
        <label>
          الحد اليومي بالجيجابايت
          <input
            type="number"
            min="0"
            step="0.1"
            value={dailyLimitGb}
            onChange={(event) => setDailyLimitGb(event.target.value)}
          />
        </label>

        <label className="full-span">
          allowlist packages
          <textarea
            rows={6}
            value={allowlistText}
            onChange={(event) => setAllowlistText(event.target.value)}
            placeholder={"com.android.phone\ncom.google.android.apps.messaging"}
          />
        </label>

        <button type="submit" disabled={saving}>
          {saving ? "جارٍ الحفظ..." : "حفظ السياسة"}
        </button>
        {message ? <p className="muted full-span">{message}</p> : null}
      </form>
    </section>
  );
}
