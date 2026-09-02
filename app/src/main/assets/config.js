// Triangle environment config — single-environment build, loaded by
// index.html before script.js.
//
// ORG_ID below is only a LAST-RESORT fallback now (used by the old
// classic username/password demo login and one legacy org1-claim check —
// see script.js). Every real Google-auth admin/trainee login now routes
// using their OWN actual organization id, stored on their user record —
// this file never needs to be edited again when a new organization is
// created in the app.
window.TXP_CONFIG = {
  FIREBASE_URL: 'https://triangle-apk-default-rtdb.firebaseio.com',
  FIREBASE_WEB_API_KEY: 'AIzaSyBvZeKsYjS-Y9qgHhQxRddsU7qUZmgYoro',
  ORG_ID: 'org1'
};
