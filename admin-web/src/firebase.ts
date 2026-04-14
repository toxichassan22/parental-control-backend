import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import { getFunctions, httpsCallable } from "firebase/functions";
import type { CommandType, PairingTokenResponse, PolicyRecord } from "./types";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
const functions = getFunctions(app, import.meta.env.VITE_FIREBASE_FUNCTIONS_REGION ?? "us-central1");

export const createPairingToken = httpsCallable<
  { deviceName: string },
  PairingTokenResponse
>(functions, "createPairingToken");

export const issueCommand = httpsCallable<
  { deviceId: string; type: CommandType; payload?: Record<string, unknown> },
  { commandId: string; commandVersion: number }
>(functions, "issueCommand");

export const upsertPolicy = httpsCallable<
  { deviceId: string; policy: Omit<PolicyRecord, "policyVersion" | "updatedAtServer"> },
  { policyVersion: number }
>(functions, "upsertPolicy");

