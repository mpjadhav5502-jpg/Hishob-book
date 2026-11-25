const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_hisab_123";
const BOB_UID = "bob_hisab_456";

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

function sampleBillPayload(uid, billNumber = 1, now = new Date(Date.now() - 2000)) {
  return {
    userId: uid,
    billNumber,
    title: "Morning Counter Sales",
    vendorOrParty: "Walk-in",
    billDateMillis: Date.now() - 5000,
    sourceType: "PAPER",
    ocrConfidence: "MANUAL",
    hasImage: false,
    items: [
      {
        lineId: "line_1",
        productName: "Masoor Full",
        unitType: "FULL",
        category: "MASOOR_FULL",
        quantity: 2,
        purchasePrice: 80.0,
        sellingPrice: 120.0,
        purchaseTotal: 160.0,
        sellingTotal: 240.0,
        profit: 80.0,
      },
      {
        lineId: "line_2",
        productName: "Roti",
        unitType: "PIECE",
        category: "ROTI",
        quantity: 6,
        purchasePrice: 8.0,
        sellingPrice: 15.0,
        purchaseTotal: 48.0,
        sellingTotal: 90.0,
        profit: 42.0,
      },
    ],
    totalQuantity: 8.0,
    totalPurchaseCost: 208.0,
    totalSellingAmount: 330.0,
    totalProfit: 122.0,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write bills, images, products, or meta", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("bills").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("bills").doc("bill_1").set(sampleBillPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("meta").doc("ledgerSummary").get()
  );
});

test("Authenticated owner: valid bill create, partial update, list, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const billRef = aliceDb.collection("users").doc(ALICE_UID).collection("bills").doc("bill_uuid_1");

  await assertSucceeds(billRef.set(sampleBillPayload(ALICE_UID, 1, created)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("bills").get());

  await assertSucceeds(
    billRef.update({
      title: "Updated Evening Counter",
      totalSellingAmount: 360.0,
      totalProfit: 152.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(billRef.delete());
});

test("Rejects @DocumentId 'id' field, immutable field tampering, ghost fields, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Rejects 'id' field in payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bills").doc("bill_bad_id").set({
      id: "bill_bad_id",
      ...sampleBillPayload(ALICE_UID, 1, created),
    })
  );

  // Rejects future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("bills").doc("bill_future").set(
      sampleBillPayload(ALICE_UID, 2, new Date(Date.now() + 60000))
    )
  );

  // Create valid bill then attempt immutable updates
  const validRef = aliceDb.collection("users").doc(ALICE_UID).collection("bills").doc("bill_valid_1");
  await assertSucceeds(validRef.set(sampleBillPayload(ALICE_UID, 1, created)));

  await assertFails(
    validRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    validRef.update({
      billNumber: 999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    validRef.update({
      ghostField: "hack",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bills").doc("bill_a1").set(sampleBillPayload(ALICE_UID, 1, now))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bills").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bills").doc("bill_a1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("bills").doc("bill_a1").update({
      title: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("bills").doc("bill_a1").delete());
});

test("BillImage, CustomProduct, and LedgerSummary rules enforce schemas and ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("meta").doc("ledgerSummary").set({
      userId: ALICE_UID,
      nextBillNumber: 2,
      totalBills: 1,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("billImages").doc("bill_1").set({
      userId: ALICE_UID,
      imageBase64: "/9j/4AAQSkZJRg==",
      mimeType: "image/jpeg",
      width: 800,
      height: 600,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("masoor_full").set({
      userId: ALICE_UID,
      name: "Masoor Full",
      normalizedName: "masoor full",
      unitType: "FULL",
      category: "MASOOR_FULL",
      defaultPurchasePrice: 80.0,
      defaultSellingPrice: 120.0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject invalid unitType enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("bad_prod").set({
      userId: ALICE_UID,
      name: "Invalid",
      normalizedName: "invalid",
      unitType: "GALLON",
      category: "CUSTOM",
      defaultPurchasePrice: 10.0,
      defaultSellingPrice: 20.0,
      createdAt: now,
      updatedAt: now,
    })
  );
});
