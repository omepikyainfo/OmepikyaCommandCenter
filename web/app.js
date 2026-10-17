(() => {
  "use strict";
  const $ = (selector) => document.querySelector(selector);
  const form = $("#command-form");
  const input = $("#command");
  const count = $("#char-count");
  const result = $("#result");
  const historyList = $("#history");
  const historyKey = "omepikya.web.history.v1";
  const notesKey = "omepikya.web.notes.v1";

  function platformName() {
    const ua = navigator.userAgent || "";
    if (/iPhone|iPad|iPod/i.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1)) return "iPhone / iPad";
    if (/Android/i.test(ua)) return "Android";
    if (/Windows/i.test(ua)) return "Windows";
    if (/Macintosh|Mac OS X/i.test(ua)) return "macOS";
    if (/Linux/i.test(ua)) return "Linux";
    return "Web browser";
  }

  function capabilities() {
    const items = [
      ["Command interface", "available"],
      ["Open websites", "available"],
      ["Local history", "available"],
      ["Native app launching", "limited"],
      ["System settings control", "limited"],
      ["Background automation", "limited"]
    ];
    $("#capabilities").replaceChildren(...items.map(([label, state]) => {
      const el = document.createElement("span");
      el.className = "capability " + state;
      el.textContent = label;
      el.title = state === "limited" ? "Requires a native app or OS-specific permission." : "Available in this browser prototype.";
      return el;
    }));
  }

  function readHistory() {
    try {
      const data = JSON.parse(localStorage.getItem(historyKey) || "[]");
      return Array.isArray(data) ? data.filter(x => x && typeof x.command === "string" && typeof x.time === "number").slice(0, 12) : [];
    } catch (_) { return []; }
  }

  function renderHistory() {
    const items = readHistory();
    historyList.replaceChildren();
    if (!items.length) {
      const li = document.createElement("li");
      li.className = "empty";
      li.textContent = "Your recent commands will appear here.";
      historyList.append(li);
      return;
    }
    items.forEach(item => {
      const li = document.createElement("li");
      li.append(document.createTextNode(item.command));
      const time = document.createElement("time");
      time.dateTime = new Date(item.time).toISOString();
      time.textContent = new Date(item.time).toLocaleString();
      li.append(time);
      historyList.append(li);
    });
  }

  function show(title, message) {
    result.replaceChildren();
    const dot = document.createElement("span");
    dot.className = "result-dot";
    const content = document.createElement("div");
    const strong = document.createElement("strong");
    strong.textContent = title;
    const p = document.createElement("p");
    p.textContent = message;
    content.append(strong, p);
    result.append(dot, content);
  }

  function openWeb(url, label) {
    const win = window.open(url, "_blank", "noopener,noreferrer");
    if (win) {
      show("Opening " + label, "Your browser was asked to open a new tab. If it was blocked, allow pop-ups for this page.");
    } else {
      show(label + " link ready", "Your browser blocked the new tab. Copy or open this URL: " + url);
    }
  }

  function execute(command) {
    const text = command.trim();
    const lower = text.toLowerCase();
    if (!text) return;
    if (/^(open|launch|start)\s+youtube( app)?$/.test(lower) || lower === "open yt") {
      openWeb("https://www.youtube.com/", "YouTube");
    } else if (/^(search( the web)? for |google )/.test(lower)) {
      const query = text.replace(/^(search( the web)? for |google )/i, "").trim();
      if (!query) { show("Add a search query", "Type what you want to search for, then run the command again."); return; }
      openWeb("https://www.google.com/search?q=" + encodeURIComponent(query), "web search");
    } else if (/^(open|launch|start)\s+https?:\/\//i.test(text)) {
      const match = text.match(/https?:\/\/\S+/i);
      try {
        const url = new URL(match[0]);
        if (!["http:", "https:"].includes(url.protocol)) throw new Error("Unsupported protocol");
        openWeb(url.href, url.hostname);
      } catch (_) { show("Invalid web address", "Use a valid http or https URL."); }
    } else if (/^(make a note|note|remember):?\s*/i.test(text)) {
      const note = text.replace(/^(make a note|note|remember):?\s*/i, "").trim();
      if (!note) { show("What should I note?", "Add the note after “Make a note:”."); return; }
      try {
        const notes = JSON.parse(localStorage.getItem(notesKey) || "[]");
        const safeNotes = Array.isArray(notes) ? notes.filter(n => n && typeof n.text === "string") : [];
        safeNotes.unshift({text: note, time: Date.now()});
        localStorage.setItem(notesKey, JSON.stringify(safeNotes.slice(0, 100)));
        show("Note saved on this device", "Your note is stored locally in this browser only. It has not been synced.");
      } catch (_) { show("Could not save note", "Local storage may be disabled or full in this browser."); }
    } else if (/^(open|launch|start)\s+/.test(lower)) {
      show("Native app launching isn't available here", "This browser can't reliably launch arbitrary installed apps. Use a supported website link, or the native client when available.");
    } else {
      show("Command not supported yet", "This cross-platform preview currently supports YouTube, web searches, URLs, and local notes. Device control and automations are not connected.");
    }

    try {
      const items = readHistory();
      items.unshift({command: text, time: Date.now()});
      localStorage.setItem(historyKey, JSON.stringify(items.slice(0, 12)));
      renderHistory();
    } catch (_) { /* UI still works if storage is disabled. */ }
  }

  input.addEventListener("input", () => { count.textContent = input.value.length + " / 1000"; });
  form.addEventListener("submit", event => { event.preventDefault(); execute(input.value); });
  document.querySelectorAll("[data-command]").forEach(button => {
    button.addEventListener("click", () => {
      const command = button.dataset.command;
      input.value = command;
      count.textContent = input.value.length + " / 1000";
      input.focus();
      if (command === "Open YouTube") execute(command);
    });
  });
  $("#clear-history").addEventListener("click", () => {
    try { localStorage.removeItem(historyKey); } catch (_) {}
    renderHistory();
    show("History cleared", "This browser's local command history has been cleared. Notes are not affected.");
  });

  $("#device-name").textContent = platformName();
  capabilities();
  renderHistory();
})();