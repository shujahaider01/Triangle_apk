// Real-time replacement for push-worker/drain.js's polling: fires the
// instant a new entry is written to /pushQueue, instead of waiting for the
// next 5-minute GitHub Actions tick. This is the "Phase 4 Cloud Function"
// script.js's own comments (see app/src/main/assets/script.js, around
// _enqueuePush) have been referring to since Phase 3.
//
// Same data shape and send logic as push-worker/drain.js — see that file
// for the full writeup of deviceTokens/pushQueue and why admin's user id is
// hardcoded to 99. Keep the two in sync if either changes; push-worker now
// only runs on manual dispatch, as a fallback for whenever this function is
// down or a token wasn't registered yet at trigger time.
const { onValueCreated } = require('firebase-functions/v2/database');
const { setGlobalOptions } = require('firebase-functions/v2');
const admin = require('firebase-admin');

admin.initializeApp();
setGlobalOptions({ maxInstances: 10 });

const ADMIN_USER_ID = '99';
const DATABASE_INSTANCE = 'triangle-apk-default-rtdb';

// FCM error codes that mean the token is permanently dead — safe to remove
// it so nothing keeps trying to push to it.
const DEAD_TOKEN_CODES = new Set([
  'messaging/invalid-registration-token',
  'messaging/registration-token-not-registered',
]);

exports.onPushQueued = onValueCreated(
  { ref: '/pushQueue/{pushId}', instance: DATABASE_INSTANCE },
  async (event) => {
    const entry = event.data.val();
    if (!entry) return;

    const db = admin.database();
    const tokenKey = entry.targetType === 'admin' ? ADMIN_USER_ID : String(entry.targetId);

    try {
      const tokenSnap = await db.ref(`deviceTokens/${tokenKey}`).once('value');
      const record = tokenSnap.val();
      const token = record && record.token;

      // No device registered for this user (yet) — nothing to send. Unlike
      // the polling worker there's no "come back in 5 minutes" retry here,
      // so this is genuinely a one-shot best-effort attempt; that's fine,
      // the in-app notification (already saved before this ever queues) is
      // unaffected either way.
      if (!token) return;

      try {
        await admin.messaging().send({
          token,
          // Data-only message, no top-level "notification" key —
          // TxpMessagingService.kt's onMessageReceived() is the single code
          // path that builds and shows the system notification in every
          // app state (foreground/background/killed all read message.data
          // the same way). See that file's class doc for why a
          // "notification" payload would break that.
          data: {
            title: entry.title || 'Triangle',
            body: entry.body || '',
            type: entry.type || '',
            notifId: entry.notifId || '',
            isAdmin: entry.isAdmin ? 'true' : 'false',
          },
          android: { priority: 'high' },
        });
      } catch (e) {
        if (DEAD_TOKEN_CODES.has(e.code)) {
          await db.ref(`deviceTokens/${tokenKey}`).remove();
        } else {
          console.error(`[onPushQueued] ${event.params.pushId}: send failed`, e);
        }
      }
    } finally {
      await event.data.ref.remove();
    }
  }
);
