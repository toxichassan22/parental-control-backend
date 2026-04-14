import crypto from "node:crypto";
import { onCall, HttpsError, onRequest } from "firebase-functions/https";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/scheduler";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import {
  FieldValue,
  Filter,
  Timestamp,
  getFirestore,
  type Firestore,
  type Transaction,
} from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { DateTime } from "luxon";

initializeApp();

const db = getFirestore();
const auth = getAuth();
const messaging = getMessaging();

const DEFAULT_DAILY_LIMIT_BYTES = 2 * 1024 * 1024 * 1024;

interface PairingTokenDoc {
  adminId: string;
  deviceName: string;
  secretHash: string;
  backupCodeHash: string;
  expiresAt: Timestamp;
  used: boolean;
  createdAt: FieldValue;
}

function requireAdminAuth(uid?: string) {
  if (!uid) {
    throw new HttpsError("unauthenticated", "Admin authentication is required.");
  }

  return uid;
}

function sha256(value: string) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function randomToken(size = 24) {
  return crypto.randomBytes(size).toString("hex");
}

function randomBackupCode() {
  return crypto.randomBytes(4).toString("hex").toUpperCase();
}

function computeServerWindow(adminTimezone: string, nowMs = Date.now()) {
  const zonedNow = DateTime.fromMillis(nowMs, { zone: adminTimezone || "UTC" });
  const start = zonedNow.startOf("day");
  const end = start.plus({ days: 1 });

  return {
    dayKey: start.toFormat("yyyyLLdd"),
    windowStartMs: start.toUTC().toMillis(),
    windowEndMs: end.toUTC().toMillis(),
    serverTimeMs: nowMs,
  };
}

async function ensureAdminOwnsDevice(tx: Transaction, deviceId: string, adminId: string) {
  const deviceRef = db.collection("devices").doc(deviceId);
  const deviceSnap = await tx.get(deviceRef);

  if (!deviceSnap.exists) {
    throw new HttpsError("not-found", "Device not found.");
  }

  if (deviceSnap.get("adminId") !== adminId) {
    throw new HttpsError("permission-denied", "You do not own this device.");
  }

  return { deviceRef, deviceSnap };
}

async function pushDeviceNotification(deviceId: string, data: Record<string, string>) {
  const deviceSnap = await db.collection("devices").doc(deviceId).get();
  const fcmToken = deviceSnap.get("fcmToken") as string | undefined;

  if (!fcmToken) {
    return;
  }

  try {
    await messaging.send({
      token: fcmToken,
      data,
    });
  } catch (error) {
    logger.warn("Failed to send FCM message", { deviceId, error });
  }
}

export const createPairingToken = onCall(async (request) => {
  const adminId = requireAdminAuth(request.auth?.uid);
  const deviceName = String(request.data?.deviceName ?? "Managed Device").trim();

  const tokenId = crypto.randomUUID();
  const pairingSecret = randomToken(16);
  const backupCode = randomBackupCode();
  const expiresAt = Timestamp.fromMillis(Date.now() + 10 * 60 * 1000);

  const doc: PairingTokenDoc = {
    adminId,
    deviceName,
    secretHash: sha256(pairingSecret),
    backupCodeHash: sha256(backupCode),
    expiresAt,
    used: false,
    createdAt: FieldValue.serverTimestamp(),
  };

  await db.collection("pairingTokens").doc(tokenId).set(doc);

  return {
    tokenId,
    pairingSecret,
    backupCode,
    expiresAt: expiresAt.toMillis(),
  };
});

export const exchangePairingToken = onRequest(async (request, response) => {
  if (request.method !== "POST") {
    response.status(405).json({ error: "method-not-allowed" });
    return;
  }

  const {
    tokenId,
    pairingSecret,
    backupCode,
    deviceName,
    appVersion,
    model,
    manufacturer,
  } = request.body ?? {};

  if (!tokenId || (!pairingSecret && !backupCode)) {
    response.status(400).json({ error: "invalid-request" });
    return;
  }

  try {
    const pairingRef = db.collection("pairingTokens").doc(String(tokenId));

    const result = await db.runTransaction(async (tx) => {
      const pairingSnap = await tx.get(pairingRef);

      if (!pairingSnap.exists) {
        throw new HttpsError("not-found", "Pairing token not found.");
      }

      const pairingData = pairingSnap.data() as PairingTokenDoc;
      if (pairingData.used) {
        throw new HttpsError("failed-precondition", "Pairing token already used.");
      }

      if (pairingData.expiresAt.toMillis() < Date.now()) {
        throw new HttpsError("deadline-exceeded", "Pairing token expired.");
      }

      const secretMatches = pairingSecret && pairingData.secretHash === sha256(String(pairingSecret));
      const backupMatches = backupCode && pairingData.backupCodeHash === sha256(String(backupCode));

      if (!secretMatches && !backupMatches) {
        throw new HttpsError("permission-denied", "Invalid pairing credentials.");
      }

      const deviceId = crypto.randomUUID();
      const deviceRef = db.collection("devices").doc(deviceId);
      const policyRef = deviceRef.collection("policy").doc("current");
      const deviceAuthUid = `device:${deviceId}`;

      tx.set(deviceRef, {
        adminId: pairingData.adminId,
        name: String(deviceName ?? pairingData.deviceName ?? "Managed Device"),
        status: "STALE",
        healthScore: 40,
        locked: false,
        vpnGranted: false,
        overlayGranted: false,
        usageGranted: false,
        accessibilityEnabled: false,
        batteryOptimizationIgnored: false,
        appVersion: String(appVersion ?? "0.1.0"),
        model: String(model ?? "unknown"),
        manufacturer: String(manufacturer ?? "unknown"),
        latestPolicyVersion: 1,
        latestCommandVersion: 0,
        createdAt: FieldValue.serverTimestamp(),
        lastSeenAt: FieldValue.serverTimestamp(),
      });

      tx.set(policyRef, {
        policyVersion: 1,
        updatedAtServer: FieldValue.serverTimestamp(),
        dailyLimitBytes: DEFAULT_DAILY_LIMIT_BYTES,
        allowlistPackages: [
          "com.android.phone",
          "com.google.android.apps.messaging",
        ],
        enforcementEnabled: true,
        adminTimezone: "UTC",
        resetMode: "SERVER_WINDOW",
      });

      tx.update(pairingRef, {
        used: true,
        usedAt: FieldValue.serverTimestamp(),
        consumedByDeviceId: deviceId,
      });

      return { deviceId, adminId: pairingData.adminId, deviceAuthUid };
    });

    const customToken = await auth.createCustomToken(result.deviceAuthUid, {
      role: "device",
      deviceId: result.deviceId,
      adminId: result.adminId,
    });

    response.status(200).json({
      deviceId: result.deviceId,
      customToken,
    });
  } catch (error) {
    logger.error("Pairing exchange failed", error);
    const code = error instanceof HttpsError ? 400 : 500;
    response.status(code).json({
      error: error instanceof Error ? error.message : "pairing-failed",
    });
  }
});

export const issueCommand = onCall(async (request) => {
  const adminId = requireAdminAuth(request.auth?.uid);
  const deviceId = String(request.data?.deviceId ?? "");
  const type = String(request.data?.type ?? "");
  const payload = request.data?.payload ?? {};

  if (!deviceId || !["LOCK", "UNLOCK", "SYNC_POLICY", "PING"].includes(type)) {
    throw new HttpsError("invalid-argument", "Invalid command request.");
  }

  const outcome = await db.runTransaction(async (tx) => {
    const { deviceRef, deviceSnap } = await ensureAdminOwnsDevice(tx, deviceId, adminId);
    const nextCommandVersion = Number(deviceSnap.get("latestCommandVersion") ?? 0) + 1;
    const commandId = crypto.randomUUID();
    const commandRef = deviceRef.collection("commands").doc(commandId);

    tx.set(commandRef, {
      commandId,
      deviceId,
      type,
      payload,
      issuedAtServer: FieldValue.serverTimestamp(),
      commandVersion: nextCommandVersion,
      expiresAt: Timestamp.fromMillis(Date.now() + 24 * 60 * 60 * 1000),
    });

    tx.update(deviceRef, {
      latestCommandVersion: nextCommandVersion,
      lastCommandIssuedAt: FieldValue.serverTimestamp(),
    });

    return { commandId, commandVersion: nextCommandVersion };
  });

  await pushDeviceNotification(deviceId, {
    signal: "COMMAND_AVAILABLE",
    commandVersion: String(outcome.commandVersion),
  });

  return outcome;
});

export const upsertPolicy = onCall(async (request) => {
  const adminId = requireAdminAuth(request.auth?.uid);
  const deviceId = String(request.data?.deviceId ?? "");
  const policy = request.data?.policy;

  if (!deviceId || !policy) {
    throw new HttpsError("invalid-argument", "Device and policy are required.");
  }

  const outcome = await db.runTransaction(async (tx) => {
    const { deviceRef, deviceSnap } = await ensureAdminOwnsDevice(tx, deviceId, adminId);
    const nextPolicyVersion = Number(deviceSnap.get("latestPolicyVersion") ?? 0) + 1;
    const policyRef = deviceRef.collection("policy").doc("current");

    tx.set(
      policyRef,
      {
        policyVersion: nextPolicyVersion,
        updatedAtServer: FieldValue.serverTimestamp(),
        dailyLimitBytes: Number(policy.dailyLimitBytes ?? DEFAULT_DAILY_LIMIT_BYTES),
        allowlistPackages: Array.isArray(policy.allowlistPackages) ? policy.allowlistPackages : [],
        enforcementEnabled: Boolean(policy.enforcementEnabled),
        adminTimezone: String(policy.adminTimezone ?? "UTC"),
        resetMode: "SERVER_WINDOW",
      },
      { merge: true },
    );

    tx.update(deviceRef, {
      latestPolicyVersion: nextPolicyVersion,
      lastPolicyUpdatedAt: FieldValue.serverTimestamp(),
    });

    return { policyVersion: nextPolicyVersion };
  });

  await pushDeviceNotification(deviceId, {
    signal: "POLICY_UPDATED",
    policyVersion: String(outcome.policyVersion),
  });

  return outcome;
});

export const pruneExpiredPairingTokens = onSchedule("every 24 hours", async () => {
  const snapshot = await db
    .collection("pairingTokens")
    .where(
      Filter.or(
        Filter.where("used", "==", true),
        Filter.where("expiresAt", "<", Timestamp.now()),
      ),
    )
    .get();

  if (snapshot.empty) {
    return;
  }

  const batch = db.batch();
  snapshot.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
});

export const markDeviceSeen = onCall(async (request) => {
  const deviceId = request.auth?.token.deviceId as string | undefined;
  const adminId = request.auth?.token.adminId as string | undefined;

  if (!deviceId || !adminId || request.auth?.token.role !== "device") {
    throw new HttpsError("permission-denied", "Device authentication is required.");
  }

  const {
    status,
    healthScore,
    vpnGranted,
    overlayGranted,
    usageGranted,
    batteryOptimizationIgnored,
    accessibilityEnabled,
    appVersion,
    locked,
    fcmToken,
  } = request.data ?? {};

  await db.collection("devices").doc(deviceId).set(
    {
      adminId,
      status: String(status ?? "STALE"),
      healthScore: Number(healthScore ?? 0),
      vpnGranted: Boolean(vpnGranted),
      overlayGranted: Boolean(overlayGranted),
      usageGranted: Boolean(usageGranted),
      batteryOptimizationIgnored: Boolean(batteryOptimizationIgnored),
      accessibilityEnabled: Boolean(accessibilityEnabled),
      appVersion: String(appVersion ?? "0.1.0"),
      locked: Boolean(locked),
      fcmToken: typeof fcmToken === "string" ? fcmToken : FieldValue.delete(),
      lastSeenAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );

  const policySnapshot = await db.collection("devices").doc(deviceId).collection("policy").doc("current").get()
  const adminTimezone = policySnapshot.get("adminTimezone") as string | undefined

  return computeServerWindow(adminTimezone ?? "UTC");
});

export async function writeUsageDaily(
  firestore: Firestore,
  deviceId: string,
  dayKey: string,
  estimatedBytes: number,
  source: "NETWORK_STATS" | "TRAFFIC_STATS",
  lockedCount: number,
) {
  await firestore.collection("devices").doc(deviceId).collection("usageDaily").doc(dayKey).set(
    {
      deviceId,
      dayKey,
      estimatedBytes,
      source,
      lockedCount,
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
}
