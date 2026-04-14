import type { DeviceRecord } from "../types";
import { formatDate } from "../lib/time";

interface DeviceListProps {
  devices: DeviceRecord[];
  selectedDeviceId?: string;
  onSelect: (deviceId: string) => void;
}

export function DeviceList({ devices, selectedDeviceId, onSelect }: DeviceListProps) {
  return (
    <aside className="device-list">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Devices</p>
          <h2>الأجهزة</h2>
        </div>
        <span className="metric-pill">{devices.length}</span>
      </div>

      {devices.length === 0 ? (
        <div className="empty-state">لا توجد أجهزة مرتبطة بهذا الحساب بعد.</div>
      ) : (
        devices.map((device) => (
          <button
            className={`device-card ${selectedDeviceId === device.id ? "device-card--active" : ""}`}
            key={device.id}
            onClick={() => onSelect(device.id)}
            type="button"
          >
            <div className="device-card__top">
              <strong>{device.name}</strong>
              <span className={`status-chip status-chip--${device.status.toLowerCase()}`}>{device.status}</span>
            </div>
            <div className="device-card__meta">
              <span>Health {device.healthScore}</span>
              <span>{device.locked ? "Locked" : "Open"}</span>
            </div>
            <div className="device-card__footer">آخر ظهور: {formatDate(device.lastSeenAt)}</div>
          </button>
        ))
      )}
    </aside>
  );
}

