const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_uid_1";
const OWNER_EMAIL = "shahprince0021@gmail.com";
const ALICE_UID = "alice_123";
const ALICE_EMAIL = "alice@example.com";
const BOB_UID = "bob_456";
const BOB_EMAIL = "bob@example.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("projects").get());
  await assertFails(unauthDb.collection("subscription_plans").get());
  await assertFails(unauthDb.collection("invitations").doc("INV-1").get());
});

test("Owner with verified email can initialize profile and grant admin role", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  const ts = nowTs();
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).set({
      userId: OWNER_UID,
      email: OWNER_EMAIL,
      displayName: "Prince Shah",
      role: "owner",
      status: "active",
      planId: "custom",
      videoUsed: 0,
      voiceUsed: 0,
      imageUsed: 0,
      scriptUsed: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    ownerDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: ALICE_EMAIL,
      grantedBy: OWNER_UID,
      createdAt: ts,
    })
  );
});

test("Email spoofing attack: unverified owner email is rejected", async () => {
  const spoofDb = testEnv.authenticatedContext("attacker_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();

  const ts = nowTs();
  await assertFails(
    spoofDb.collection("admins").doc("attacker_uid").set({
      uid: "attacker_uid",
      email: OWNER_EMAIL,
      grantedBy: "attacker_uid",
      createdAt: ts,
    })
  );
});

test("Uninvited visitor cannot self-register in users collection", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID, {
    email: BOB_EMAIL,
    email_verified: true,
  }).firestore();

  const ts = nowTs();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "user",
      status: "active",
      planId: "free",
      videoUsed: 0,
      voiceUsed: 0,
      imageUsed: 0,
      scriptUsed: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Invited user can redeem pending invitation atomically and cannot escalate role", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("invitations").doc("PRINCE-2026").set({
      inviteCode: "PRINCE-2026",
      invitedEmail: ALICE_EMAIL,
      invitedName: "Alice",
      role: "user",
      planId: "basic",
      status: "pending",
      createdByUid: OWNER_UID,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();

  // Missing invite code (e.g. "HAFG") is rejected on get
  await assertFails(aliceDb.collection("invitations").doc("HAFG").get());

  // Attempting to promote self to admin during invite redemption must fail
  const badBatch = aliceDb.batch();
  badBatch.update(aliceDb.collection("invitations").doc("PRINCE-2026"), {
    status: "accepted",
    acceptedByUid: ALICE_UID,
    updatedAt: ts,
  });
  badBatch.set(aliceDb.collection("users").doc(ALICE_UID), {
    userId: ALICE_UID,
    email: ALICE_EMAIL,
    displayName: "Alice",
    role: "admin",
    status: "active",
    planId: "basic",
    videoUsed: 0,
    voiceUsed: 0,
    imageUsed: 0,
    scriptUsed: 0,
    inviteCode: "PRINCE-2026",
    createdAt: ts,
    updatedAt: ts,
  });
  await assertFails(badBatch.commit());

  // Valid atomic redemption with role='user' and matching planId succeeds
  const goodBatch = aliceDb.batch();
  goodBatch.update(aliceDb.collection("invitations").doc("PRINCE-2026"), {
    status: "accepted",
    acceptedByUid: ALICE_UID,
    updatedAt: ts,
  });
  goodBatch.set(aliceDb.collection("users").doc(ALICE_UID), {
    userId: ALICE_UID,
    email: ALICE_EMAIL,
    displayName: "Alice",
    role: "user",
    status: "active",
    planId: "basic",
    videoUsed: 0,
    voiceUsed: 0,
    imageUsed: 0,
    scriptUsed: 0,
    inviteCode: "PRINCE-2026",
    createdAt: ts,
    updatedAt: ts,
  });
  await assertSucceeds(goodBatch.commit());

  // Alice cannot promote herself or change her own plan
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      planId: "pro",
      updatedAt: ts,
    })
  );
});

test("Mixed-case Google auth email can redeem lowercase email-locked invitation", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("invitations").doc("PRINCE-CASE").set({
      inviteCode: "PRINCE-CASE",
      invitedEmail: "princeshah3472@gmail.com",
      invitedName: "Prince",
      role: "user",
      planId: "free",
      status: "pending",
      createdByUid: OWNER_UID,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const princeDb = testEnv.authenticatedContext("prince_uid_case", {
    email: "PrinceShah3472@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(princeDb.collection("invitations").doc("PRINCE-CASE").get());

  const batch = princeDb.batch();
  batch.update(princeDb.collection("invitations").doc("PRINCE-CASE"), {
    status: "accepted",
    acceptedByUid: "prince_uid_case",
    updatedAt: ts,
  });
  batch.set(princeDb.collection("users").doc("prince_uid_case"), {
    userId: "prince_uid_case",
    email: "PrinceShah3472@gmail.com",
    displayName: "Prince",
    role: "user",
    status: "active",
    planId: "free",
    videoUsed: 0,
    voiceUsed: 0,
    imageUsed: 0,
    scriptUsed: 0,
    inviteCode: "PRINCE-CASE",
    createdAt: ts,
    updatedAt: ts,
  });
  await assertSucceeds(batch.commit());
});

test("Cross-user isolation on projects and upgrade requests", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      status: "active",
      planId: "free",
      videoUsed: 0,
      voiceUsed: 0,
      imageUsed: 0,
      scriptUsed: 0,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "user",
      status: "active",
      planId: "free",
      videoUsed: 0,
      voiceUsed: 0,
      imageUsed: 0,
      scriptUsed: 0,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("projects").doc("proj_alice").set({
      userId: ALICE_UID,
      title: "Alice Script",
      type: "script",
      prompt: "Write a documentary intro",
      outputText: "In the beginning...",
      status: "completed",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();

  await assertSucceeds(aliceDb.collection("projects").where("userId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("projects").doc("proj_alice").get());
  await assertFails(bobDb.collection("projects").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Chat sessions, messages, and assistant notes are strictly isolated per user", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      status: "active",
      planId: "free",
      videoUsed: 0,
      voiceUsed: 0,
      imageUsed: 0,
      scriptUsed: 0,
      chatUsed: 0,
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      email: BOB_EMAIL,
      displayName: "Bob",
      role: "user",
      status: "active",
      planId: "free",
      videoUsed: 0,
      voiceUsed: 0,
      imageUsed: 0,
      scriptUsed: 0,
      chatUsed: 0,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: ALICE_EMAIL, email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: BOB_EMAIL, email_verified: true }).firestore();

  await assertSucceeds(
    aliceDb.collection("chat_sessions").doc("sess_1").set({
      userId: ALICE_UID,
      title: "Quantum Mechanics Help",
      language: "en",
      modelUsed: "gemini-3.8-flash",
      lastMessagePreview: "Explain wave-particle duality",
      messageCount: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("chat_sessions").doc("sess_1").collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      role: "user",
      text: "Explain wave-particle duality",
      hasImageAttachment: false,
      modelUsed: "gemini-3.8-flash",
      createdAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("assistant_notes").doc("note_1").set({
      userId: ALICE_UID,
      title: "Video shoot checklist",
      content: "Charge mic and prepare Himalayas script",
      kind: "reminder",
      dueLabel: "Tomorrow 8 AM",
      isCompleted: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(bobDb.collection("chat_sessions").doc("sess_1").get());
  await assertFails(
    bobDb.collection("chat_sessions").doc("sess_1").collection("messages").doc("msg_2").set({
      userId: BOB_UID,
      role: "user",
      text: "Hijack",
      hasImageAttachment: false,
      createdAt: ts,
    })
  );
  await assertFails(bobDb.collection("assistant_notes").doc("note_1").get());
  await assertSucceeds(aliceDb.collection("chat_sessions").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("assistant_notes").where("userId", "==", ALICE_UID).get());
});

test("Admin can configure extended system_config and plan chatLimit, non-admin denied", async () => {
  const ts = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: ALICE_EMAIL,
      displayName: "Alice",
      role: "user",
      status: "active",
      planId: "free",
      videoUsed: 0,
      voiceUsed: 0,
      imageUsed: 0,
      scriptUsed: 0,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: ALICE_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    ownerDb.collection("system_config").doc("global").set({
      maintenanceMode: false,
      maintenanceMessage: "Maintenance",
      invitesEnabled: true,
      videoStudioEnabled: true,
      voiceStudioEnabled: true,
      imageStudioEnabled: true,
      scriptStudioEnabled: true,
      aiChatEnabled: true,
      voiceAssistantEnabled: true,
      phoneActionsEnabled: true,
      activeChatModel: "gemini-3.8-flash",
      maxVideoDurationSeconds: 120,
      videoProviderConfigured: false,
      videoProviderName: "On-Device Storyboard MP4 Composer",
      updatedByUid: OWNER_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    ownerDb.collection("subscription_plans").doc("pro").set({
      name: "PRO PLAN",
      description: "Pro tier",
      priceUsd: 39,
      billingPeriod: "monthly",
      videoLimit: 150,
      voiceLimit: 300,
      imageLimit: 150,
      scriptLimit: 500,
      chatLimit: 1500,
      includedTools: ["AI Chat", "Video Studio"],
      isActive: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    aliceDb.collection("system_config").doc("global").update({
      maxVideoDurationSeconds: 300,
      updatedByUid: ALICE_UID,
      updatedAt: ts,
    })
  );

  // Alice's legacy profile without chatUsed can initialize chatUsed to 1
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      chatUsed: 1,
      updatedAt: ts,
    })
  );

  // Alice cannot jump chatUsed by +5 in a single write
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      chatUsed: 6,
      updatedAt: ts,
    })
  );
});


