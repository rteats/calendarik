import fs from "node:fs";
import assert from "node:assert/strict";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { collection, doc, getDoc, getDocs, setDoc, updateDoc, serverTimestamp } from "firebase/firestore";

// Test only an isolated local emulator. No production Firebase credentials used.
const env = await initializeTestEnvironment({
  projectId: "demo-calendarik",
  firestore: { rules: fs.readFileSync("firestore.rules", "utf8") },
});

try {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (admin) => {
    await setDoc(doc(admin.firestore(), "approvedUsers", "sasha"), { enabled: true });
    await setDoc(doc(admin.firestore(), "approvedUsers", "nastya"), { enabled: true });
  });

  const sasha = env.authenticatedContext("sasha", { email: "sasha@example.test" }).firestore();
  const nastya = env.authenticatedContext("nastya", { email: "nastya@example.test" }).firestore();
  const outsider = env.authenticatedContext("outsider", { email: "outsider@example.test" }).firestore();
  const anonymous = env.unauthenticatedContext().firestore();
  const pairId = "local-test-pair";
  const owner = doc(sasha, "pairs", pairId);
  const request = doc(nastya, "pairs", pairId, "requests", "nastya");

  // No client can add itself to the approved-user allowlist.
  await assertFails(setDoc(doc(outsider, "approvedUsers", "outsider"), { enabled: true }));
  await assertFails(setDoc(doc(outsider, "pairs", "bad"), {
    creatorUid: "outsider", creatorRole: 1,
    memberUids: ["outsider"], joinerUid: "", schema: 1,
  }));

  await assertSucceeds(setDoc(owner, {
    creatorUid: "sasha", creatorRole: 1,
    memberUids: ["sasha"], joinerUid: "", schema: 1,
  }));
  await assertFails(getDoc(doc(nastya, "pairs", pairId)));
  await assertFails(getDoc(doc(anonymous, "pairs", pairId)));

  // Joiner can request, but cannot approve itself.
  await assertSucceeds(setDoc(request, {
    requesterUid: "nastya", requesterEmail: "nastya@example.test", status: "pending",
  }));
  await assertFails(updateDoc(doc(nastya, "pairs", pairId), {
    memberUids: ["sasha", "nastya"], joinerUid: "nastya",
  }));
  await assertSucceeds(updateDoc(owner, {
    memberUids: ["sasha", "nastya"], joinerUid: "nastya",
  }));
  await assertSucceeds(updateDoc(doc(sasha, "pairs", pairId, "requests", "nastya"), {
    status: "accepted",
  }));
  await assertSucceeds(getDoc(doc(nastya, "pairs", pairId)));

  // Owner alone can publish own encrypted payload, not the peer's payload.
  const payload = (uid) => ({
    writerUid: uid, schema: 1,
    nonce: "abcdefghijklmnop",
    ciphertext: "This-is-only-test-ciphertext-AAAAAA",
    updatedAt: serverTimestamp(),
  });
  await assertFails(setDoc(doc(nastya, "pairs", pairId, "snapshots", "sasha"), payload("sasha")));
  await assertFails(setDoc(doc(outsider, "pairs", pairId, "snapshots", "outsider"), payload("outsider")));
  await assertSucceeds(setDoc(doc(sasha, "pairs", pairId, "snapshots", "sasha"), payload("sasha")));
  await assertSucceeds(setDoc(doc(nastya, "pairs", pairId, "snapshots", "nastya"), payload("nastya")));
  await assertFails(setDoc(doc(nastya, "pairs", pairId, "snapshots", "nastya"), {
    ...payload("nastya"), plaintextTitle: "private calendar entry",
  }));
  await assertFails(getDoc(doc(outsider, "pairs", pairId, "snapshots", "sasha")));
  await assertSucceeds(getDoc(doc(nastya, "pairs", pairId, "snapshots", "sasha")));
  await assertFails(updateDoc(doc(nastya, "pairs", pairId), { creatorRole: 2 }));

  console.log("Firestore allowlist, pairing, encryption-only schema and owner isolation OK");
} finally {
  await env.cleanup();
}
