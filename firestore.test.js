const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read or write students", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("students").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("students").doc("1").set({
      userId: ALICE_UID,
      year: "2026",
      roll: "1",
      name: "John Doe",
      active: true,
    })
  );
});

test("Authenticated user: cannot read or write another user's students", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("students").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("students").doc("1").set({
      userId: ALICE_UID,
      year: "2026",
      roll: "1",
      name: "John Doe",
      active: true,
    })
  );
});

test("Authenticated user: can create and read own students", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("students").doc("1").set({
      userId: ALICE_UID,
      year: "2026",
      roll: "1",
      name: "Alice Student",
      active: true,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("students").get());
});

test("Authenticated user: can create and read own attendance records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("attendance").doc("2026_2026-10-13_1").set({
      userId: ALICE_UID,
      year: "2026",
      date: "2026-10-13",
      roll: "1",
      status: "P",
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("attendance").get());
});

test("Authenticated user: rejects invalid attendance status", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("attendance").doc("rec_bad").set({
      userId: ALICE_UID,
      year: "2026",
      date: "2026-10-13",
      roll: "1",
      status: "INVALID_STATUS",
    })
  );
});
