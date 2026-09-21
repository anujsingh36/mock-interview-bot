/* ------------------------------------------------------------------
   Tiny vanilla frontend. No build step, no framework — it just talks
   to the Spring Boot REST API with fetch().
   ------------------------------------------------------------------ */

const store = {
  get token() { return localStorage.getItem("tk_token"); },
  set token(v) { v ? localStorage.setItem("tk_token", v) : localStorage.removeItem("tk_token"); },
  get name() { return localStorage.getItem("tk_name") || ""; },
  set name(v) { v ? localStorage.setItem("tk_name", v) : localStorage.removeItem("tk_name"); },
};

const state = { sessionId: null, topic: "Core Java", level: "beginner", total: 5 };

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

async function api(path, { method = "GET", body } = {}) {
  const res = await fetch(path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(store.token ? { Authorization: `Bearer ${store.token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401 || res.status === 403) {
    if (store.token) signOut();
    throw new Error("Please sign in again.");
  }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || "Something went wrong.");
  return data;
}

/* ---------------- view switching ---------------- */
function show(view) {
  $$(".view").forEach((v) => v.classList.add("hidden"));
  $(`#view-${view}`).classList.remove("hidden");
  $("#nav").classList.toggle("hidden", !store.token);
  window.scrollTo({ top: 0, behavior: "smooth" });
}

$$("[data-go]").forEach((b) => b.addEventListener("click", () => {
  const target = b.dataset.go;
  if (target === "history") loadHistory();
  if (target === "setup") resetSetup();
  show(target);
}));

/* ---------------- auth ---------------- */
$$(".tab").forEach((tab) => tab.addEventListener("click", () => {
  $$(".tab").forEach((t) => t.classList.toggle("active", t === tab));
  $("#form-login").classList.toggle("hidden", tab.dataset.tab !== "login");
  $("#form-register").classList.toggle("hidden", tab.dataset.tab !== "register");
  $("#auth-error").textContent = "";
}));

$("#form-login").addEventListener("submit", (e) => {
  e.preventDefault();
  const f = new FormData(e.target);
  authCall("/api/auth/login", { email: f.get("email"), password: f.get("password") });
});

$("#form-register").addEventListener("submit", (e) => {
  e.preventDefault();
  const f = new FormData(e.target);
  authCall("/api/auth/register", {
    name: f.get("name"), email: f.get("email"), password: f.get("password"),
  });
});

async function authCall(path, body) {
  $("#auth-error").textContent = "";
  try {
    const res = await api(path, { method: "POST", body });
    store.token = res.token;
    store.name = res.name;
    await afterSignIn();
  } catch (err) {
    $("#auth-error").textContent = err.message;
  }
}

$("#signout").addEventListener("click", signOut);

function signOut() {
  store.token = null;
  store.name = null;
  state.sessionId = null;
  show("auth");
}

/* ---------------- setup ---------------- */
async function afterSignIn() {
  const me = await api("/api/interview/me");
  $("#who").textContent = `hey, ${me.name.split(" ")[0]}`;
  $("#greeting").textContent =
    "Five questions, no audience. Answer badly if you have to — that's how you find the gaps.";

  const chips = $("#topic-chips");
  chips.innerHTML = "";
  me.topics.forEach((topic, i) => {
    const b = document.createElement("button");
    b.className = "chip" + (i === 0 ? " active" : "");
    b.textContent = topic;
    b.dataset.topic = topic;
    b.addEventListener("click", () => {
      $$("#topic-chips .chip").forEach((c) => c.classList.remove("active"));
      b.classList.add("active");
      $("#topic-custom").value = "";
      state.topic = topic;
    });
    chips.appendChild(b);
  });
  state.topic = me.topics[0];
  show("setup");
}

$("#topic-custom").addEventListener("input", (e) => {
  if (e.target.value.trim()) {
    $$("#topic-chips .chip").forEach((c) => c.classList.remove("active"));
    state.topic = e.target.value.trim();
  }
});

$$("#level-chips .chip").forEach((chip) => chip.addEventListener("click", () => {
  $$("#level-chips .chip").forEach((c) => c.classList.remove("active"));
  chip.classList.add("active");
  state.level = chip.dataset.level;
}));

function resetSetup() {
  $("#setup-error").textContent = "";
  $("#feedback-list").innerHTML = "";
  $("#answer-text").value = "";
}

$("#start-btn").addEventListener("click", async () => {
  const btn = $("#start-btn");
  $("#setup-error").textContent = "";
  btn.disabled = true;
  btn.textContent = "Thinking of a question…";
  try {
    const res = await api("/api/interview/start", {
      method: "POST",
      body: { topic: state.topic, level: state.level },
    });
    state.sessionId = res.sessionId;
    state.total = res.totalQuestions;
    $("#feedback-list").innerHTML = "";
    paintQuestion(res.question, res.questionNumber, res.totalQuestions);
    show("interview");
  } catch (err) {
    $("#setup-error").textContent = err.message;
  } finally {
    btn.disabled = false;
    btn.textContent = "Start the round";
  }
});

/* ---------------- interview ---------------- */
function paintQuestion(question, number, total) {
  $("#question-text").textContent = question;
  $("#progress-text").textContent = `Question ${number} of ${total}`;
  const dots = $("#progress-dots");
  dots.innerHTML = "";
  for (let i = 1; i <= total; i++) {
    const d = document.createElement("span");
    d.className = "dot" + (i < number ? " done" : "");
    dots.appendChild(d);
  }
  $("#answer-text").value = "";
  $("#answer-text").focus();
}

$("#answer-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const answer = $("#answer-text").value.trim();
  $("#interview-error").textContent = "";
  if (!answer) {
    $("#interview-error").textContent = "Write something first — even a guess counts.";
    return;
  }

  const btn = $("#submit-answer");
  btn.disabled = true;
  $("#thinking").classList.remove("hidden");

  try {
    const res = await api(`/api/interview/${state.sessionId}/answer`, {
      method: "POST", body: { answer },
    });

    addFeedback($("#question-text").textContent, res.score, res.feedback, res.modelAnswer);

    if (res.finished) {
      $("#final-score").textContent = res.averageScore;
      $("#final-summary").textContent = res.summary || "";
      show("result");
    } else {
      paintQuestion(res.nextQuestion, res.questionNumber, res.totalQuestions);
    }
  } catch (err) {
    $("#interview-error").textContent = err.message;
  } finally {
    btn.disabled = false;
    $("#thinking").classList.add("hidden");
  }
});

function addFeedback(question, score, feedback, modelAnswer) {
  const cls = score >= 7 ? "good" : score >= 4 ? "mid" : "low";
  const node = document.createElement("article");
  node.className = "feedback";
  node.innerHTML = `
    <header>
      <p class="q"></p>
      <span class="score ${cls}">${score}/10</span>
    </header>
    <p class="fb"></p>
    ${modelAnswer ? `<div class="model-answer"><strong>What a strong answer sounds like:</strong> <span class="ma"></span></div>` : ""}
  `;
  node.querySelector(".q").textContent = question;
  node.querySelector(".fb").textContent = feedback;
  if (modelAnswer) node.querySelector(".ma").textContent = modelAnswer;
  $("#feedback-list").prepend(node);
}

/* ---------------- history ---------------- */
async function loadHistory() {
  const list = $("#history-list");
  list.innerHTML = `<p class="empty">Pulling up your rounds…</p>`;
  try {
    const rounds = await api("/api/interview/history");
    if (!rounds.length) {
      list.innerHTML = `<p class="empty">Nothing here yet. Your first round is the hardest to start.</p>`;
      return;
    }
    list.innerHTML = "";
    rounds.forEach((r) => {
      const cls = r.averageScore >= 7 ? "good" : r.averageScore >= 4 ? "mid" : "low";
      const card = document.createElement("div");
      card.className = "card history-item";
      card.innerHTML = `
        <div>
          <h3></h3>
          <p class="meta"></p>
          <p class="meta summary-line"></p>
        </div>
        <span class="score ${cls}">${r.averageScore}/10</span>
      `;
      card.querySelector("h3").textContent = `${r.topic} · ${r.level}`;
      card.querySelector(".meta").textContent =
        `${r.startedAt} — ${r.answered} answered${r.finished ? "" : " (left unfinished)"}`;
      card.querySelector(".summary-line").textContent = r.summary || "";
      list.appendChild(card);
    });
  } catch (err) {
    list.innerHTML = `<p class="error">${err.message}</p>`;
  }
}

/* ---------------- boot ---------------- */
(async function boot() {
  if (!store.token) { show("auth"); return; }
  try { await afterSignIn(); } catch { signOut(); }
})();
