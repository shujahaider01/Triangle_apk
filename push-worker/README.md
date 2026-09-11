# Push worker

Drains Firebase RTDB `/pushQueue` and sends the real FCM push each entry
represents. This is the missing "Phase 4 Cloud Function" `script.js`'s own
comments have been referring to since Phase 3 — see `drain.js` for the full
data-shape writeup.

Runs on a schedule via `.github/workflows/push-notifications.yml` (every 5
minutes), not as a long-lived process.

## One-time setup

1. **Generate a service account key** for the `triangle-apk` Firebase
   project: Firebase Console → Project Settings (gear icon) → Service
   Accounts tab → "Generate new private key". This downloads a JSON file —
   keep it private, never commit it.
2. **Add two GitHub repo secrets** (Settings → Secrets and variables →
   Actions → New repository secret):
   - `FIREBASE_SERVICE_ACCOUNT_JSON` — paste the *entire contents* of the
     downloaded JSON file.
   - `FIREBASE_DB_URL` — `https://triangle-apk-default-rtdb.firebaseio.com`
     (matches `FIREBASE_URL` in `app/src/main/assets/config.js`).
3. That's it — the workflow starts running automatically every 5 minutes.
   You can trigger a manual run from the Actions tab (workflow has
   `workflow_dispatch` enabled) to test it right away instead of waiting.

## Local testing

```bash
cd push-worker
npm install
FIREBASE_SERVICE_ACCOUNT_JSON="$(cat /path/to/service-account.json)" \
FIREBASE_DB_URL="https://triangle-apk-default-rtdb.firebaseio.com" \
node drain.js
```

## How to verify it's working end to end

1. Trigger any in-app event that queues a push (e.g. assign a task to an
   intern) while that intern's device is backgrounded or the app is fully
   closed.
2. Check `pushQueue` in the Firebase console — a new entry should appear.
3. Run the workflow manually (Actions tab → "Push notifications" →
   "Run workflow"), or wait up to 5 minutes.
4. The queue entry should disappear, and a real system notification should
   land on the device.
