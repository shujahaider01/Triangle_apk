# Cloud Functions

Real-time replacement for `push-worker`'s 5-minute polling: `onPushQueued`
fires the instant something is written to `/pushQueue`, and sends the FCM
push immediately. See `index.js` for the full writeup.

## One-time setup

1. **Upgrade `triangle-apk` to the Blaze plan** — Firebase Console → hit
   "Upgrade" (bottom-left) → Blaze. Cloud Functions don't run on the free
   Spark plan at all, but Blaze still includes the same free monthly quota
   (2M invocations, 400K GB-seconds compute) — you're not charged unless
   usage grows far past that, which a small team app won't.
2. **Install the Firebase CLI** (if you don't have it):
   ```bash
   npm install -g firebase-tools
   ```
3. **Log in**:
   ```bash
   firebase login
   ```
4. **Install function dependencies and deploy**:
   ```bash
   cd functions
   npm install
   cd ..
   firebase deploy --only functions
   ```

That's it — `.firebaserc` already points this at the `triangle-apk` project,
and `firebase.json` already points at this `functions/` folder.

## After deploying

Once this is live, `push-worker`'s GitHub Actions schedule has been turned
off (see `.github/workflows/push-notifications.yml` — `workflow_dispatch`
only now) to avoid both paths racing to send/delete the same queue entry.
It's kept around as a manual fallback: if this function is ever down, run
that workflow by hand to drain whatever built up in `pushQueue` meanwhile.

## Redeploying after a change

```bash
firebase deploy --only functions
```

## Viewing logs

```bash
firebase functions:log
```
or Firebase Console → Functions → onPushQueued → Logs.
