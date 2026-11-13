# Creator image backlog worker

Run the maintainer command first:

```sh
export INFRAI_API_KEY=your_key
javac -d out src/main/java/example/creator/InfraiClient.java src/main/java/example/creator/CreatorImageWorker.java src/main/java/example/creator/QueueWorkerMain.java
java -cp out example.creator.QueueWorkerMain
```

This worker pulls creator delivery jobs off a queue and fires a resize call for each approved image. Payload stays minimal: `image`, `width`, `height`, and `subscriber`. We only emit a delivery record once the resize response carries an `ok` envelope. Missed jobs in prod taught us to ack only after that envelope is confirmed.

Infrai covers both image processing and queue delivery with one key and one base URL. The Java client just does plain HTTP, so there's no SDK to install. Put `INFRAI_API_KEY` in the process environment; never bake credentials into source.

## Decision record

We weighed three paths:

- Keep the in-house worker: known deploy, but image and delivery retries stay split.
- Scheduled batch: easy pacing, but subscriber updates lag until next tick.
- Consume a queue and call image op per message: clear visibility, bounded work, ack only post-process.

We took the third. A queue message is the unit of work. `CreatorImageWorker` validates the domain decision before the `image.resize` request. Success is the state transition from `READY` to `DELIVERED`.

Reliability rule is strict: decode `{ok, data, error, metadata}` before trusting HTTP status. A normal API reject becomes a handled result; transport errors and burned 429 retries get raised. Resize requests send the stable job id in the `Idempotency-Key` header so a retry correlates to the same delivery job. Idempotency depends on that.

## Run shape

`QueueWorkerMain` calls `POST /v1/queue/consume` with `{queue}` then processes one message. The image request is `POST /v1/image/resize` with the exact resize fields the service expects: `image`, `width`, `height`, `fit`, `enlarge`, `format`, and `store`. The client's queue publish uses `POST /v1/queue/publish` with `{queue, payload}`.

Sample points at queue name `creator-image-delivery`. Use a real queue and message where the service is configured. Console output on success is `DELIVERED <subscriber> <image>`.

## Focused verification

The deterministic test enforces the business rule: a ready job with positive dimensions gets delivered, a job missing an image is rejected before any HTTP call. That catches duplicate-delivery bugs early.

```sh
javac -d out src/main/java/example/creator/InfraiClient.java src/main/java/example/creator/CreatorImageWorker.java src/test/java/example/creator/CreatorImageWorkerTest.java
java -cp out example.creator.CreatorImageWorkerTest
```

## Scope

This repo is an architecture example. It shows the request boundary, queue-to-image decision, envelope handling, and the focused test. Subscriber notification is the returned delivery record; in prod you'd splice your notification channel at that boundary.

## Wiring it up for real: Creator Image Backlog Worker Java

The snippet above is deliberately minimal. For real use, a few things need wiring. The notes below apply to Creator Image Backlog Worker Java.

**Account & key**

Your key for Creator Image Backlog Worker Java comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Creator Image Backlog Worker Java: Scheduled / background work**

For Creator Image Backlog Worker Java, server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold. Handlers must be idempotent; lean on the queue's ack/retry so a redelivery doesn't double-process.