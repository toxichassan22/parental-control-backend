export type DeviceStatus = "HEALTHY" | "WARNING" | "DEGRADED" | "LOCKED" | "STALE";
export type CommandType = "LOCK" | "UNLOCK" | "SYNC_POLICY" | "PING";

export interface DeviceHeartbeat {
  status: DeviceStatus;
  healthScore: number;
  lastSeenAt?: number;
  vpnGranted: boolean;
  overlayGranted: boolean;
  usageGranted: boolean;
  batteryOptimizationIgnored: boolean;
  accessibilityEnabled: boolean;
  appVersion: string;
  locked: boolean;
  latestPolicyVersion: number;
  latestCommandVersion: number;
  fcmToken?: string;
}

export interface DeviceRecord extends DeviceHeartbeat {
  id: string;
  adminId: string;
  name: string;
  createdAt?: number;
}

export interface PolicyRecord {
  policyVersion: number;
  updatedAtServer?: number;
  dailyLimitBytes: number;
  allowlistPackages: string[];
  enforcementEnabled: boolean;
  adminTimezone: string;
  resetMode: "SERVER_WINDOW";
}

export interface UsageDaily {
  id: string;
  deviceId: string;
  dayKey: string;
  estimatedBytes: number;
  source: "NETWORK_STATS" | "TRAFFIC_STATS";
  lockedCount: number;
  updatedAt?: number;
}

export interface DeviceEvent {
  id: string;
  deviceId: string;
  type: string;
  severity: "info" | "warning" | "critical";
  createdAt?: number;
  metadata?: Record<string, unknown>;
}

export interface PairingTokenResponse {
  tokenId: string;
  pairingSecret: string;
  backupCode: string;
  expiresAt: number;
}

