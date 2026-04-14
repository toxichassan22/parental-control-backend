import {
  collection,
  doc,
  limit,
  onSnapshot,
  orderBy,
  query,
  where,
} from "firebase/firestore";
import { useEffect, useState } from "react";
import { db } from "../firebase";
import type { DeviceEvent, DeviceRecord, PolicyRecord, UsageDaily } from "../types";

function asMillis(value: unknown): number | undefined {
  if (!value) {
    return undefined;
  }

  if (typeof value === "number") {
    return value;
  }

  if (typeof value === "object" && value !== null && "toMillis" in value) {
    return (value as { toMillis: () => number }).toMillis();
  }

  return undefined;
}

export function useDevices(adminId?: string) {
  const [devices, setDevices] = useState<DeviceRecord[]>([]);

  useEffect(() => {
    if (!adminId) {
      setDevices([]);
      return;
    }

    const devicesQuery = query(collection(db, "devices"), where("adminId", "==", adminId));
    return onSnapshot(devicesQuery, (snapshot) => {
      setDevices(
        snapshot.docs.map((item) => {
          const data = item.data();
          const lastSeenAt = asMillis(data.lastSeenAt);
          const derivedStatus: DeviceRecord["status"] =
            lastSeenAt && Date.now() - lastSeenAt > 180_000 ? "STALE" : data.status ?? "STALE";

          return {
            id: item.id,
            name: data.name ?? "Unnamed Device",
            adminId: data.adminId,
            status: derivedStatus,
            healthScore: derivedStatus === "STALE" ? Math.min(data.healthScore ?? 0, 40) : data.healthScore ?? 0,
            lastSeenAt,
            vpnGranted: !!data.vpnGranted,
            overlayGranted: !!data.overlayGranted,
            usageGranted: !!data.usageGranted,
            batteryOptimizationIgnored: !!data.batteryOptimizationIgnored,
            accessibilityEnabled: !!data.accessibilityEnabled,
            appVersion: data.appVersion ?? "0.0.0",
            locked: !!data.locked,
            latestPolicyVersion: data.latestPolicyVersion ?? 0,
            latestCommandVersion: data.latestCommandVersion ?? 0,
            createdAt: asMillis(data.createdAt),
          };
        }),
      );
    });
  }, [adminId]);

  return devices;
}

export function useDeviceDetail(deviceId?: string) {
  const [policy, setPolicy] = useState<PolicyRecord | null>(null);
  const [usage, setUsage] = useState<UsageDaily[]>([]);
  const [events, setEvents] = useState<DeviceEvent[]>([]);

  useEffect(() => {
    if (!deviceId) {
      setPolicy(null);
      setUsage([]);
      setEvents([]);
      return;
    }

    const policyUnsub = onSnapshot(doc(db, "devices", deviceId, "policy", "current"), (snapshot) => {
      if (!snapshot.exists()) {
        setPolicy(null);
        return;
      }

      const data = snapshot.data();
      setPolicy({
        policyVersion: data.policyVersion ?? 0,
        updatedAtServer: asMillis(data.updatedAtServer),
        dailyLimitBytes: data.dailyLimitBytes ?? 0,
        allowlistPackages: data.allowlistPackages ?? [],
        enforcementEnabled: !!data.enforcementEnabled,
        adminTimezone: data.adminTimezone ?? "UTC",
        resetMode: "SERVER_WINDOW",
      });
    });

    const usageQuery = query(
      collection(db, "devices", deviceId, "usageDaily"),
      orderBy("dayKey", "desc"),
      limit(7),
    );
    const usageUnsub = onSnapshot(usageQuery, (snapshot) => {
      setUsage(
        snapshot.docs.map((item) => {
          const data = item.data();
          return {
            id: item.id,
            deviceId,
            dayKey: data.dayKey ?? item.id,
            estimatedBytes: data.estimatedBytes ?? 0,
            source: data.source ?? "TRAFFIC_STATS",
            lockedCount: data.lockedCount ?? 0,
            updatedAt: asMillis(data.updatedAt),
          };
        }),
      );
    });

    const eventsQuery = query(
      collection(db, "devices", deviceId, "events"),
      orderBy("createdAt", "desc"),
      limit(10),
    );
    const eventsUnsub = onSnapshot(eventsQuery, (snapshot) => {
      setEvents(
        snapshot.docs.map((item) => {
          const data = item.data();
          return {
            id: item.id,
            deviceId,
            type: data.type ?? "UNKNOWN",
            severity: data.severity ?? "info",
            createdAt: asMillis(data.createdAt),
            metadata: data.metadata ?? {},
          };
        }),
      );
    });

    return () => {
      policyUnsub();
      usageUnsub();
      eventsUnsub();
    };
  }, [deviceId]);

  return { policy, usage, events };
}
