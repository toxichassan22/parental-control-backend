# Parental Control SaaS v1

Monorepo for a parental control system with three parts:

- `android-agent/`: Android app installed on managed devices.
- `admin-web/`: React dashboard for the supervising admin.
- `firebase/`: Firestore rules and Cloud Functions.

## Architecture

- Android devices pair with the admin account using a single-use QR token.
- Devices authenticate with Firebase using a custom token minted by Cloud Functions.
- Devices cache policy locally and continue enforcing `Hard lock` offline.
- Admins read device state from Firestore and issue commands through callable Cloud Functions.
- Commands are delivered via FCM with polling and foreground sync fallback.

## Repository Layout

- `android-agent/` Android Studio project with local persistence, sync, and enforcement services.
- `admin-web/` Vite + React + TypeScript dashboard.
- `firebase/` Firebase config, rules, and TypeScript functions.

## Environment

### Admin Web

Create `admin-web/.env.local`:

```bash
VITE_FIREBASE_API_KEY=...
VITE_FIREBASE_AUTH_DOMAIN=...
VITE_FIREBASE_PROJECT_ID=...
VITE_FIREBASE_STORAGE_BUCKET=...
VITE_FIREBASE_MESSAGING_SENDER_ID=...
VITE_FIREBASE_APP_ID=...
VITE_FIREBASE_FUNCTIONS_REGION=us-central1
```

### Android Agent

Add Firebase config to `android-agent/app/google-services.json`, update `PAIRING_ENDPOINT` in `android-agent/app/build.gradle.kts`, and set the following in `android-agent/local.properties` if needed:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

### Firebase Functions

Create `firebase/.env` if you want to override defaults in emulator scripts. Production secrets should be configured in Firebase.

## Local Development

### Admin Web

```bash
cd admin-web
npm install
npm run dev
```

### Firebase

```bash
cd firebase
npm install
npm run build
firebase emulators:start
```

### Android Agent

Open `android-agent/` in Android Studio, sync Gradle, add your `google-services.json`, and run on a physical Android device.

## Current Scope

This repository implements the v1 architecture:

- single admin
- 5-10 Android phones/tablets
- hard lock enforcement
- estimated usage tracking
- FCM + polling + resume sync
- server-authoritative daily windows with offline cache

The system does not claim to prevent uninstall, force stop, or Safe Mode bypass.
