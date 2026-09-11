# Push worker

Drains Firebase RTDB `/pushQueue` and sends the real FCM push each entry
represents — see `drain.js` for the full data-shape writeup.

**This is now a manual fallback, not the primary path.** Real-time delivery
is handled by the `onPushQueued` Cloud Function (see `../functions/`), which
fires instantly instead of waiting on a poll. Run this workflow by hand
(Actions tab → "Push notifications" → "Run workflow") only if that function
is down, not yet deployed, or you want to drain a backlog that built up
while it was unavailable.

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
3. That's it — this workflow only ever runs when you trigger it manually
   (Actions tab → "Run workflow") — see above.

## Local testing

```bash
cd push-worker
npm install
FIREBASE_SERVICE_ACCOUNT_JSON="$(cat /path/to/service-account.json)" \
FIREBASE_DB_URL="https://triangle-apk-default-rtdb.firebaseio.com" \
node drain.js
```

## How to verify this fallback path specifically

1. Trigger any in-app event that queues a push (e.g. assign a task to an
   intern) while that intern's device is backgrounded or the app is fully
   closed.
2. Check `pushQueue` in the Firebase console — a new entry should appear
   (and, with `onPushQueued` deployed, disappear again almost immediately —
   if you want to test this workflow instead, temporarily disable that
   function first, or just check between the write and its trigger firing).
3. Run the workflow manually (Actions tab → "Push notifications" →
   "Run workflow").
4. The queue entry should disappear, and a real system notification should
   land on the device.
