import { createServer } from "node:http";
import { randomUUID } from "node:crypto";
import { generateCloudReply, ProviderNotConfiguredError } from "./provider.js";

const MAX_BODY_BYTES = 16 * 1024;
const MAX_MESSAGE_CHARS = 4000;
const MAX_HISTORY_MESSAGES = 12;
const MAX_SESSIONS = 1000;
const SESSION_TTL_MS = 30 * 60 * 1000;
const RATE_WINDOW_MS = 60 * 1000;
const DEFAULT_RATE_LIMIT = 30;

function sendJson(res, status, payload, extraHeaders = {}) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    ...extraHeaders
  });
  res.end(JSON.stringify(payload));
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    let bytes = 0;
    let settled = false;
    req.on("data", chunk => {
      if (settled) return;
      bytes += chunk.length;
      if (bytes > MAX_BODY_BYTES) {
        settled = true;
        reject(Object.assign(new Error("Request body is too large."), { statusCode: 413 }));
        return;
      }
      body += chunk.toString("utf8");
    });
    req.on("end", () => {
      if (settled) return;
      if (!body) return reject(Object.assign(new Error("A JSON body is required."), { statusCode: 400 }));
      try {
        resolve(JSON.parse(body));
      } catch {
        reject(Object.assign(new Error("Request body must be valid JSON."), { statusCode: 400 }));
      }
    });
    req.on("error", reject);
  });
}

function allowedOrigin(req, env) {
  const origin = req.headers.origin;
  if (!origin) return { ok: true, headers: {} };
  const configured = (env.TINY_ALLOWED_ORIGINS || "")
    .split(",").map(value => value.trim()).filter(Boolean);
  if (!configured.includes(origin)) return { ok: false, headers: {} };
  return {
    ok: true,
    headers: {
      "Access-Control-Allow-Origin": origin,
      "Vary": "Origin",
      "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization"
    }
  };
}

function pruneSessions(sessions, now) {
  for (const [id, session] of sessions) {
    if (now - session.lastSeen > SESSION_TTL_MS) sessions.delete(id);
  }
  while (sessions.size > MAX_SESSIONS) sessions.delete(sessions.keys().next().value);
}

export function createTinyServer({
  env = process.env,
  generateReply = messages => generateCloudReply(messages, env),
  now = () => Date.now()
} = {}) {
  const sessions = new Map();
  const rateBuckets = new Map();

  return createServer(async (req, res) => {
    const origin = allowedOrigin(req, env);
    if (!origin.ok) return sendJson(res, 403, { error: "Origin is not allowed." });
    const headers = origin.headers;

    if (req.method === "OPTIONS") {
      if (!req.headers.origin) return sendJson(res, 204, {}, headers);
      return sendJson(res, 204, {}, headers);
    }

    const url = new URL(req.url || "/", "http://localhost");
    if (req.method === "GET" && url.pathname === "/healthz") {
      return sendJson(res, 200, {
        ok: true,
        providerConfigured: Boolean(env.OPENAI_API_KEY),
        mode: "hybrid"
      }, headers);
    }

    if (req.method !== "POST" || url.pathname !== "/api/tiny/chat") {
      return sendJson(res, 404, { error: "Not found." }, headers);
    }

    const time = now();
    const ip = req.socket.remoteAddress || "unknown";
    const bucket = rateBuckets.get(ip) || { startedAt: time, count: 0 };
    if (time - bucket.startedAt >= RATE_WINDOW_MS) {
      bucket.startedAt = time;
      bucket.count = 0;
    }
    bucket.count += 1;
    rateBuckets.set(ip, bucket);
    if (bucket.count > Number(env.TINY_RATE_LIMIT || DEFAULT_RATE_LIMIT)) {
      return sendJson(res, 429, { error: "Too many requests. Please try again shortly." }, headers);
    }
    if (rateBuckets.size > MAX_SESSIONS) {
      for (const [key, value] of rateBuckets) {
        if (time - value.startedAt > RATE_WINDOW_MS) rateBuckets.delete(key);
      }
    }

    let body;
    try {
      body = await readJson(req);
    } catch (error) {
      if (!res.headersSent && !res.destroyed) {
        sendJson(res, error.statusCode || 400, { error: error.message || "Invalid request." }, headers);
      }
      return;
    }

    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return sendJson(res, 400, { error: "Request must be a JSON object." }, headers);
    }
    if (typeof body.message !== "string") {
      return sendJson(res, 400, { error: "message must be a string." }, headers);
    }
    const message = body.message.trim();
    if (!message || message.length > MAX_MESSAGE_CHARS) {
      return sendJson(res, 400, { error: `message must contain 1-${MAX_MESSAGE_CHARS} characters.` }, headers);
    }

    let sessionId = body.sessionId;
    if (sessionId !== undefined && sessionId !== null && sessionId !== "") {
      if (typeof sessionId !== "string" || !/^[a-zA-Z0-9_-]{1,80}$/.test(sessionId)) {
        return sendJson(res, 400, { error: "sessionId has an invalid format." }, headers);
      }
    } else {
      sessionId = randomUUID();
    }

    pruneSessions(sessions, time);
    const session = sessions.get(sessionId) || { messages: [], lastSeen: time };
    const nextMessages = [...session.messages, { role: "user", content: message }].slice(-MAX_HISTORY_MESSAGES);
    try {
      const reply = await generateReply(nextMessages);
      if (typeof reply !== "string" || !reply.trim()) throw new Error("Empty assistant response.");
      const assistantMessage = { role: "assistant", content: reply.trim().slice(0, 12000) };
      sessions.set(sessionId, {
        messages: [...nextMessages, assistantMessage].slice(-MAX_HISTORY_MESSAGES),
        lastSeen: time
      });
      pruneSessions(sessions, time);
      return sendJson(res, 200, { reply: assistantMessage.content, sessionId }, headers);
    } catch (error) {
      if (error instanceof ProviderNotConfiguredError) {
        return sendJson(res, 503, { error: "Tiny cloud conversation is not configured on the server yet." }, headers);
      }
      return sendJson(res, 502, { error: "Tiny could not reach the conversation service. Please try again." }, headers);
    }
  });
}

const isDirectRun = process.argv[1] && import.meta.url === new URL(`file://${process.argv[1]}`).href;
if (isDirectRun) {
  const port = Number(process.env.PORT || 8080);
  const host = process.env.HOST || "127.0.0.1";
  const server = createTinyServer();
  server.listen(port, host, () => {
    console.log(`Tiny API listening on http://${host}:${port}`);
    if (!process.env.OPENAI_API_KEY) console.warn("OPENAI_API_KEY is not configured; chat requests will return 503.");
  });
}
