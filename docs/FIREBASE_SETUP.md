# Firebase setup for calendarik-bff31

The attached Android client config has been integrated into
\`app/google-services.json\` and Google Services plugin 4.5.0 is applied.

The Firebase Android client config contains public project identifiers and
an API key, **not a service account private key or an encryption key**.
Firebase explicitly permits committing Firebase-only client keys when
API restrictions and Security Rules are configured.

## Project owner actions

1. [Open Firebase](https://console.firebase.google.com/project/calendarik-bff31/overview).
2. Verify project uses **Spark**, not Blaze, if zero billing is required.
3. In **Build > Firestore Database**, create a Firestore database with
   **production mode** security rules, not test mode.
4. In **Authentication > Sign-in method**, enable only the provider we will
   use for two verified identities. We have not enabled any provider from code.
5. In **Firestore > Rules**, paste \`firestore.rules\` (deny all) and publish.
   This is safe now, but blocks sync until authenticated two-user enrollment
   and E2EE are implemented.
6. Inspect the project's Google API key in Google Cloud Console and confirm
   it is restricted to Firebase services. Never add unrestricted APIs.
7. Keep all Admin SDK/service-account private keys outside this repository.

Alternatively, the owner can deploy \`firestore.rules\` using the Firebase CLI:
\`firebase login\` then \`firebase deploy --only firestore:rules\`.
This action needs an account with project access; a repository commit cannot
configure remote Firestore security rules.

## Current status

- Firebase Android SDKs, BoM and Google Services plugin are configured.
- The application runs fully offline, including events, hearts and moods.
- **Cloud writes, identity enrollment and cross-device sync are not enabled.**
  The app does not send plaintext events or reactions to Firestore.
- \`firestore.rules\` denies all until the real end-to-end encrypted
  protocol and pair-specific access control are tested.
- For now, the in-app Sasha/Nastya selector is local UI only and not secure
  identity verification.

Do not weaken Security Rules to \`allow read, write: if true\`. Never store
plain event titles, descriptions, dates, moods or counters in Firestore
until encryption and authorization are implemented and verified.

Read: https://firebase.google.com/docs/projects/api-keys

