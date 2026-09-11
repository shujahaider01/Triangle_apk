// Drains Firebase RTDB /pushQueue and sends the real FCM push each entry
// represents — this is the "Phase 4 Cloud Function" script.js's own comments
// (see app/src/main/assets/script.js, around _enqueuePush) have been
// referring to since Phase 3. Nothing writes to pushQueue except that file;
// this script's only job is to read it, send, and delete.
//
// Meant to be run periodically (see .github/workflows/push-notifications.yml
// — every 5 minutes via GitHub Actions), not as a long-lived daemon. Each
// run: fetch the whole queue once, process every entry, exit.
//
// Data shape (must match app/src/main/assets/script.js exactly):
//   deviceTokens/{userId} → { token, role, updatedAt }
//   pushQueue/{autoId}    → { targetType: 'intern'|'admin', targetId, notifId,
//                             isAdmin, type, title, body, createdAt }
//
// Every admin in this app has the fixed id 99 (see script.js's own
// `i.id !== 99` / `otherId === '99'` checks) and deviceTokens is a single
// GLOBAL collection keyed only by userId, not per-organization — so this
// mirrors an existing limitation already baked into the client's data
// model (multiple orgs' admins would collide on deviceTokens/99), not
// something introduced here.
const admin = require('firebase-admin');

const ADMIN_USER_ID = '99';
// Queue entries older than this with no resolvable device token are
// discarded rather than retried forever (e.g. a notification queued for a
// user who has never opened the app on a real device, so no token will ever
// show up at that path).
const MAX_QUEUE_AGE_MS = 24 * 60 * 60 * 1000;

// FCM error codes that mean the token is permanently dead — safe to delete
// both the queue entry AND the stale token itself, so nothing keeps trying
// to push to it forever.
const DEAD_TOKEN_CODES = new Set([
  'messaging/invalid-registration-token',
  'messaging/registration-token-not-registered',
]);

function initFirebase() {
  const databaseURL = process.env.FIREBASE_DB_URL;
  if (!databaseURL) throw new Error('FIREBASE_DB_URL env var is required');

  const credential = process.env.FIREBASE_SERVICE_ACCOUNT_JSON
    ? admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON))
    : admin.credential.applicationDefault();

  admin.initializeApp({ credential, databaseURL });
}

async function getDeviceToken(db, targetType, targetId) {
  const key = targetType === 'admin' ? ADMIN_USER_ID : String(targetId);
  const snap = await db.ref(`deviceTokens/${key}`).once('value');
  const record = snap.val();
  return record && record.token ? record.token : null;
}

async function sendPush(entry, token) {
  await admin.messaging().send({
    token,
    // Data-only message, deliberately with no top-level "notification" key —
    // TxpMessagingService.kt's onMessageReceived() is the single code path
    // that builds and shows the system notification in every app state
    // (foreground/background/killed all read message.data the same way).
    // See that file's class doc for why a "notification" payload would
    // break that.
    data: {
      title: entry.title || 'Triangle',
      body: entry.body || '',
      type: entry.type || '',
      notifId: entry.notifId || '',
      isAdmin: entry.isAdmin ? 'true' : 'false',
    },
    android: { priority: 'high' },
  });
}

async function processEntry(db, id, entry) {
  const token = await getDeviceToken(db, entry.targetType, entry.targetId);

  if (!token) {
    const age = Date.now() - (entry.createdAt || 0);
    if (age > MAX_QUEUE_AGE_MS) {
      console.log(`[drain] ${id}: no device token after ${Math.round(age / 3600000)}h, discarding`);
      await db.ref(`pushQueue/${id}`).remove();
    } else {
      console.log(`[drain] ${id}: no device token yet, leaving queued`);
    }
    return;
  }

  try {
    await sendPush(entry, token);
    console.log(`[drain] ${id}: sent`);
    await db.ref(`pushQueue/${id}`).remove();
  } catch (e) {
    const code = e && e.code;
    if (DEAD_TOKEN_CODES.has(code)) {
      console.log(`[drain] ${id}: dead token (${code}), discarding entry + token`);
      const key = entry.targetType === 'admin' ? ADMIN_USER_ID : String(entry.targetId);
      await Promise.all([
        db.ref(`pushQueue/${id}`).remove(),
        db.ref(`deviceTokens/${key}`).remove(),
      ]);
    } else {
      // Transient failure (network, quota, etc.) — leave the entry queued
      // so the next run retries it.
      console.warn(`[drain] ${id}: send failed (${code || e.message}), will retry next run`);
    }
  }
}

async function main() {
  initFirebase();
  const db = admin.database();

  const snap = await db.ref('pushQueue').once('value');
  const queue = snap.val() || {};
  const ids = Object.keys(queue);

  if (ids.length === 0) {
    console.log('[drain] pushQueue empty, nothing to do');
    return;
  }

  console.log(`[drain] processing ${ids.length} queued push(es)`);
  for (const id of ids) {
    await processEntry(db, id, queue[id]);
  }
}

main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error('[drain] fatal error', e);
    process.exit(1);
  });
