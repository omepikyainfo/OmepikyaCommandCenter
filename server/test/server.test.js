import test from "node:test";
import assert from "node:assert/strict";
import { createTinyServer } from "../src/server.js";

async function withServer(options, run) {
  const server = createTinyServer(options);
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  try {
    await run(`http://127.0.0.1:${address.port}`);
  } finally {
    await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  }
}

async function post(base, body, headers = {}) {
  return fetch(`${base}/api/tiny/chat`, {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body)
  });
}

test("health reports hybrid mode and provider readiness without exposing secrets", async () => {
  await withServer({ env: { OPENAI_API_KEY: "test-secret" } }, async base => {
    const response = await fetch(`${base}/healthz`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { ok: true, providerConfigured: true, mode: "hybrid" });
  });
});

test("chat validates message and returns a session id", async () => {
  await withServer({ generateReply: async messages => `Echo: ${messages.at(-1).content}` }, async base => {
    const response = await post(base, { message: "Hello Tiny" });
    assert.equal(response.status, 200);
    const data = await response.json();
    assert.equal(data.reply, "Echo: Hello Tiny");
    assert.match(data.sessionId, /^[0-9a-f-]{36}$/);
  });
});

test("conversation history is bounded and session-specific", async () => {
  const received = [];
  await withServer({ generateReply: async messages => {
    received.push(messages.map(item => ({ ...item })));
    return "reply";
  } }, async base => {
    await post(base, { message: "first", sessionId: "session_a" });
    await post(base, { message: "second", sessionId: "session_a" });
    await post(base, { message: "other", sessionId: "session_b" });
    assert.deepEqual(received[1].map(item => item.content), ["first", "reply", "second"]);
    assert.deepEqual(received[2].map(item => item.content), ["other"]);
  });
});

test("invalid input is rejected before calling the model", async () => {
  let called = false;
  await withServer({ generateReply: async () => { called = true; return "no"; } }, async base => {
    assert.equal((await post(base, { message: "  " })).status, 400);
    assert.equal((await post(base, { message: "x".repeat(4001) })).status, 400);
    assert.equal((await post(base, { message: "hello", sessionId: "bad session" })).status, 400);
    assert.equal(called, false);
  });
});

test("missing provider configuration returns a safe 503", async () => {
  await withServer({ env: {} }, async base => {
    const response = await post(base, { message: "hello" });
    assert.equal(response.status, 503);
    assert.match((await response.json()).error, /not configured/i);
  });
});

test("origin allowlist blocks unknown browser origins", async () => {
  await withServer({ env: { TINY_ALLOWED_ORIGINS: "https://tiny.example" } }, async base => {
    const denied = await post(base, { message: "hello" }, { origin: "https://evil.example" });
    assert.equal(denied.status, 403);
    const allowed = await post(base, { message: "hello" }, { origin: "https://tiny.example" });
    assert.equal(allowed.status, 200);
    assert.equal(allowed.headers.get("access-control-allow-origin"), "https://tiny.example");
  });
});

test("rate limit is enforced", async () => {
  await withServer({ env: { TINY_RATE_LIMIT: "1" }, generateReply: async () => "ok" }, async base => {
    assert.equal((await post(base, { message: "one" })).status, 200);
    assert.equal((await post(base, { message: "two" })).status, 429);
  });
});

test("model output is conversation only; API exposes no command execution route", async () => {
  await withServer({ generateReply: async () => "I cannot execute device actions from chat." }, async base => {
    assert.equal((await post(base, { message: "run a device command" })).status, 200);
    assert.equal((await post(base, { message: "run a device command", path: "/commands" })).status, 200);
    assert.equal((await fetch(`${base}/api/tiny/commands`)).status, 404);
  });
});
