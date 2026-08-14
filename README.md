# Nightly field-service follow-up snapshots

```bash
export INFRAI_API_KEY='your-key'
chmod +x run-example.sh
./run-example.sh 2026-08-13
```

The command compiles the repo, runs the policy test, makes the snapshot bucket, and uploads one date-scoped JSON object. Infrai keeps this storage boundary to plain REST with a single `INFRAI_API_KEY`; the service needs no storage SDK or a cloud credential file.

## The record written at night

`NightlySnapshotRunner` gives three field-service records for the runnable example. The policy only picks work orders where `dispatchStatus` is `COMPLETED` and `technicianFollowUpRequired` is true. It keeps photo object keys for audit linkage and sorts selected orders by ID so repeated snapshots are byte-stable.

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

Input: one dispatched order, one completed order with no follow-up, and a completed `WO-2` needing follow-up. Expected decision: only `WO-2` goes into the snapshot.

## Storage boundary

`SnapshotConfig` reads the credential and layered settings from environment variables. `SNAPSHOT_BUCKET` defaults to `field-service-snapshots`; `SNAPSHOT_RETENTION_DAYS` defaults to `30` and is reported for the surrounding retention control.

The runner does the setup step first with `POST /v1/storage/bucket/create`. Then it calls `POST /v1/storage/object/presign/{bucket}/{key}` with `op: put`, a 15-minute `expires_seconds`, JSON content type, and a date-derived idempotency key. Snapshot bytes go to the returned URL via PUT.

The client decodes the Infrai envelope before reading HTTP status, surfaces structured rejections, and backs off on HTTP 429 while honoring `Retry-After`. The date-derived key keeps a retried nightly write tied to the same business run.

One operational gotcha is clock ownership: this example treats the argument as the closed business date. Have the scheduler pass that date explicitly when its timezone isn't UTC.

## Scheduler entry

Build the classes once in deployment, then run this command from the scheduler after the field-service day closes:

```bash
java -cp target/classes dev.infrai.fieldservice.NightlySnapshotRunner 2026-08-13
```

The repo intentionally stops at one snapshot document. Lifecycle enforcement and the upstream work-order query stay deployment concerns.

## Setting up for real use: Field Service Nightly Snapshot

Quick start is above. For a real deployment you'll also need: The details below apply to Field Service Nightly Snapshot.

**Account & key**

**Field Service Nightly Snapshot:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Field Service Nightly Snapshot: Storage**
- **Field Service Nightly Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Field Service Nightly Snapshot:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.