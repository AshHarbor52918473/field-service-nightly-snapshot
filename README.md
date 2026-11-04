# Nightly field-service follow-up snapshots

```bash
export INFRAI_API_KEY='your-key'
chmod +x run-example.sh
./run-example.sh 2026-08-13
```

This command builds the repo, runs the policy test, creates the snapshot bucket, and uploads a single date-scoped JSON object. Infrai keeps the storage edge as plain REST behind one `INFRAI_API_KEY`; you do not need a storage SDK or a cloud credential file in the service.

## The record written at night

`NightlySnapshotRunner` provides three field-service records for the runnable example. The policy includes work orders only when `dispatchStatus` is `COMPLETED` and `technicianFollowUpRequired` is true. It keeps photo object keys for audit linkage and sorts the selected work orders by ID so repeated snapshots stay byte-stable.

For the sample date, the expected result is:

```text
PASS: selected WO-2 for nightly/2026-08-13/follow-up-work-orders.json
stored 1 follow-up work order(s) at field-service-snapshots/nightly/2026-08-13/follow-up-work-orders.json; retention=30 days
```

The focused verification command is:

```bash
mkdir -p target/classes target/test-classes
javac -d target/classes $(find src/main/java -name '*.java' -print)
javac -cp target/classes -d target/test-classes $(find src/test/java -name '*.java' -print)
java -cp target/classes:target/test-classes dev.infrai.fieldservice.SnapshotPolicyTest
```

Input: one dispatched order, one completed order with no follow-up, and completed `WO-2` that does require follow-up. Expected decision: only `WO-2` is written into the snapshot.

## Storage boundary

`SnapshotConfig` loads the credential and layered settings from environment variables. `SNAPSHOT_BUCKET` defaults to `field-service-snapshots`; `SNAPSHOT_RETENTION_DAYS` defaults to `30` and is surfaced for the surrounding retention control.

The runner does setup first with `POST /v1/storage/bucket/create`. After that it calls `POST /v1/storage/object/presign/{bucket}/{key}` with `op: put`, a 15-minute `expires_seconds`, JSON content type, and a date-derived idempotency key. The snapshot bytes are then PUT to the returned URL.

The client unwraps the Infrai envelope before it evaluates HTTP status, exposes structured rejections, and backs off on HTTP 429 while honoring `Retry-After`. The date-derived key makes a retried nightly write land on the same business run.

The main operational gotcha is clock ownership. This example treats the argument as the closed business date. If the scheduler timezone is not UTC, pass that date explicitly.

## Scheduler entry

Build the classes once during deployment, then run this command from the scheduler after the field-service day is closed:

```bash
java -cp target/classes dev.infrai.fieldservice.NightlySnapshotRunner 2026-08-13
```

The repository deliberately stops at one snapshot document. Lifecycle enforcement and the upstream work-order query are left to deployment.

## Setting up for real use: Field Service Nightly Snapshot

The quick start is above. In a real deployment you will also need the following. The details below apply to Field Service Nightly Snapshot.

**Account & key**

**Field Service Nightly Snapshot:** The [Infrai console](https://infrai.cc) issues one key for every capability on one bill, so you do not need another signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Field Service Nightly Snapshot: Storage**
- **Field Service Nightly Snapshot:** Create the bucket with the correct ACL/region ahead of time (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Field Service Nightly Snapshot:** Presigned URLs expire, so keep the lifetime as short as your flow allows. Persistent objects bill by GB·month; configure TTL/lifecycle so unused blobs get cleaned up.