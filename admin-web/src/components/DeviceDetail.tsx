import { useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { createPairingToken, issueCommand } from "../firebase";
import { formatBytes, formatDate } from "../lib/time";
import type { DeviceEvent, DeviceRecord, PolicyRecord, UsageDaily } from "../types";
import { PolicyEditor } from "./PolicyEditor";

interface DeviceDetailProps {
  device: DeviceRecord | null;
  policy: PolicyRecord | null;
  usage: UsageDaily[];
  events: DeviceEvent[];
}

export function DeviceDetail({ device, policy, usage, events }: DeviceDetailProps) {
  const [draftDeviceName, setDraftDeviceName] = useState("Child Device");
  const [pairingPayload, setPairingPayload] = useState<{
    tokenId: string;
    pairingSecret: string;
    backupCode: string;
    expiresAt: number;
  } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  async function handleCommand(type: "LOCK" | "UNLOCK" | "SYNC_POLICY" | "PING") {
    if (!device) {
      return;
    }

    setBusy(type);
    setMessage(null);

    try {
      const result = await issueCommand({
        deviceId: device.id,
        type,
      });
      setMessage(`تم إرسال ${type} برقم ${result.data.commandVersion}`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : `تعذر إرسال ${type}`);
    } finally {
      setBusy(null);
    }
  }

  async function handlePairingToken() {
    setBusy("PAIR");
    setMessage(null);

    try {
      const result = await createPairingToken({ deviceName: device?.name ?? draftDeviceName });
      setPairingPayload(result.data);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "تعذر إنشاء رمز الربط");
    } finally {
      setBusy(null);
    }
  }

  const qrPayload = pairingPayload
    ? JSON.stringify({
        tokenId: pairingPayload.tokenId,
        pairingSecret: pairingPayload.pairingSecret,
      })
    : null;

  if (!device) {
    return (
      <section className="device-detail">
        <section className="panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Pairing</p>
              <h2>ربط أول جهاز</h2>
            </div>
          </div>

          <div className="form-grid">
            <label>
              اسم الجهاز
              <input value={draftDeviceName} onChange={(event) => setDraftDeviceName(event.target.value)} />
            </label>
            <button disabled={busy === "PAIR"} onClick={handlePairingToken} type="button">
              {busy === "PAIR" ? "جارٍ الإنشاء..." : "إنشاء QR جديد"}
            </button>
            <p className="muted">
              أنشئ الرمز من هنا ثم افتح تطبيق Android على الجهاز الجديد وامسح الـ QR أو استخدم backup code.
            </p>
          </div>
        </section>

        {pairingPayload && qrPayload ? (
          <section className="panel pairing-panel">
            <div>
              <p className="eyebrow">Pairing</p>
              <h3>رمز الربط</h3>
              <p className="muted">QR صالح حتى {formatDate(pairingPayload.expiresAt)}</p>
            </div>
            <QRCodeSVG size={180} value={qrPayload} />
            <div className="pairing-secrets">
              <span>backup code: {pairingPayload.backupCode}</span>
              <span>token: {pairingPayload.tokenId}</span>
            </div>
          </section>
        ) : null}
      </section>
    );
  }

  return (
    <section className="device-detail">
      <div className="hero-card">
        <div>
          <p className="eyebrow">Device</p>
          <h2>{device.name}</h2>
          <p className="muted">
            آخر ظهور: {formatDate(device.lastSeenAt)} | health score: {device.healthScore}
          </p>
        </div>
        <div className="hero-actions">
          <button disabled={busy === "LOCK"} onClick={() => handleCommand("LOCK")} type="button">
            {busy === "LOCK" ? "..." : "Lock"}
          </button>
          <button disabled={busy === "UNLOCK"} onClick={() => handleCommand("UNLOCK")} type="button">
            {busy === "UNLOCK" ? "..." : "Unlock"}
          </button>
          <button disabled={busy === "SYNC_POLICY"} onClick={() => handleCommand("SYNC_POLICY")} type="button">
            Sync policy
          </button>
          <button disabled={busy === "PAIR"} onClick={handlePairingToken} type="button">
            New pairing QR
          </button>
        </div>
      </div>

      {message ? <div className="info-banner">{message}</div> : null}

      <div className="detail-grid">
        <section className="panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Signals</p>
              <h3>حالة الحماية</h3>
            </div>
            <span className={`status-chip status-chip--${device.status.toLowerCase()}`}>{device.status}</span>
          </div>

          <dl className="facts">
            <div><dt>VPN</dt><dd>{device.vpnGranted ? "Granted" : "Missing"}</dd></div>
            <div><dt>Overlay</dt><dd>{device.overlayGranted ? "Granted" : "Missing"}</dd></div>
            <div><dt>Usage access</dt><dd>{device.usageGranted ? "Granted" : "Missing"}</dd></div>
            <div><dt>Accessibility</dt><dd>{device.accessibilityEnabled ? "Enabled" : "Disabled"}</dd></div>
            <div><dt>Battery optimization</dt><dd>{device.batteryOptimizationIgnored ? "Ignored" : "Active"}</dd></div>
            <div><dt>App version</dt><dd>{device.appVersion}</dd></div>
          </dl>
        </section>

        <section className="panel">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Usage</p>
              <h3>Estimated usage</h3>
            </div>
          </div>
          <div className="usage-list">
            {usage.map((entry) => (
              <div className="usage-row" key={entry.id}>
                <div>
                  <strong>{entry.dayKey}</strong>
                  <p className="muted">{entry.source}</p>
                </div>
                <div className="usage-row__value">
                  <strong>{formatBytes(entry.estimatedBytes)}</strong>
                  <p className="muted">Locks: {entry.lockedCount}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>

      {pairingPayload && qrPayload ? (
        <section className="panel pairing-panel">
          <div>
            <p className="eyebrow">Pairing</p>
            <h3>رمز الربط الجديد</h3>
            <p className="muted">QR صالح حتى {formatDate(pairingPayload.expiresAt)}</p>
          </div>
          <QRCodeSVG size={180} value={qrPayload} />
          <div className="pairing-secrets">
            <span>backup code: {pairingPayload.backupCode}</span>
            <span>token: {pairingPayload.tokenId}</span>
          </div>
        </section>
      ) : null}

      <PolicyEditor device={device} policy={policy} />

      <section className="panel">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Events</p>
            <h3>أحدث التنبيهات</h3>
          </div>
        </div>
        <div className="event-list">
          {events.map((event) => (
            <div className={`event-item event-item--${event.severity}`} key={event.id}>
              <div>
                <strong>{event.type}</strong>
                <p className="muted">{formatDate(event.createdAt)}</p>
              </div>
              <code>{JSON.stringify(event.metadata ?? {}, null, 0)}</code>
            </div>
          ))}
        </div>
      </section>
    </section>
  );
}
