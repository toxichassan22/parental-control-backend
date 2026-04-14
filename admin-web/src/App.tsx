import { useState } from "react";
import { DeviceDetail } from "./components/DeviceDetail";
import { DeviceList } from "./components/DeviceList";
import { LoginGate } from "./components/LoginGate";
import { useAuthSession } from "./hooks/useAuthSession";
import { useDeviceDetail, useDevices } from "./hooks/useDevices";

export default function App() {
  const { user, loading, error, login, logout } = useAuthSession();
  const devices = useDevices(user?.uid);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | undefined>(undefined);
  const selectedDevice = devices.find((device) => device.id === selectedDeviceId) ?? devices[0] ?? null;
  const { policy, usage, events } = useDeviceDetail(selectedDevice?.id);

  if (!user) {
    return <LoginGate loading={loading} error={error} onLogin={login} />;
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Admin console</p>
          <h1>Parental Control Dashboard</h1>
        </div>
        <div className="topbar__actions">
          <span>{user.email}</span>
          <button onClick={logout} type="button">
            خروج
          </button>
        </div>
      </header>

      <div className="content-grid">
        <DeviceList
          devices={devices}
          selectedDeviceId={selectedDevice?.id}
          onSelect={(nextDeviceId) => setSelectedDeviceId(nextDeviceId)}
        />
        <DeviceDetail device={selectedDevice} policy={policy} usage={usage} events={events} />
      </div>
    </main>
  );
}

