# AI Mock Interview Bot — Spring Boot + MySQL + Google Gemini

A web app where a user signs up, picks a topic (Core Java, DBMS, Spring Boot…),
answers 5 AI-generated interview questions, and gets a score out of 10 plus
honest feedback on every answer. Every question, answer, score and session is
stored in SQL, so the user can see all their past rounds.

**Tech stack (deliberately small):**

| Layer | Choice |
|---|---|
| Backend | Java 17, Spring Boot 3.3 (Web, Data JPA, Security, Validation) |
| Database | MySQL (H2 file DB by default, so it runs with zero setup) |
| Auth | Email + password, BCrypt hashing, JWT tokens (stateless) |
| AI | Google Gemini REST API (`gemini-2.0-flash`) — free tier |
| Frontend | Plain HTML + CSS + JavaScript served by Spring Boot (no build step) |

---

## 1. What you need installed

- **JDK 17 or newer** — `java -version`
- **Maven** (or just use IntelliJ, which bundles it) — `mvn -version`
- **MySQL** — optional; skip it for the first run
- **IntelliJ IDEA Community** — free, recommended

## 2. Get a free Gemini API key

1. Go to <https://aistudio.google.com/apikey>
2. Sign in with your Google account → **Create API key**
3. Copy it. Free tier is roughly 1,500 requests/day — plenty for a demo.

**Never paste the key into the code.** Set it as an environment variable.

macOS / Linux:
```bash
export GEMINI_API_KEY="paste_your_key_here"
export JWT_SECRET="any-long-random-string-at-least-32-characters"
```

Windows (PowerShell):
```powershell
$env:GEMINI_API_KEY="paste_your_key_here"
$env:JWT_SECRET="any-long-random-string-at-least-32-characters"
```

In IntelliJ: **Run → Edit Configurations → Environment variables** and add both.

## 3. Run it

```bash
cd mock-interview-bot
mvn spring-boot:run
```

Open <http://localhost:8080> → create an account → start a round.

The first run uses an H2 file database at `./data/interviewdb` — nothing to install.
DB console (optional): <http://localhost:8080/h2-console>, JDBC URL `jdbc:h2:file:./data/interviewdb`, user `sa`, empty password.

## 4. Switch to MySQL (do this before you put it on your resume)

1. Start MySQL and create nothing — the URL creates the schema for you.
2. In `src/main/resources/application.properties`, comment the H2 block and uncomment the MySQL block, then set your root password.
3. Restart. Hibernate creates `users`, `interview_sessions` and `question_answers` automatically.

Useful queries to show in a viva:
```sql
SELECT * FROM users;
SELECT topic, level, answered_count, total_score FROM interview_sessions;
SELECT question, score, feedback FROM question_answers ORDER BY id DESC LIMIT 10;
```

---

## 5. How the code is laid out

```
src/main/java/com/mockinterview/
  MockInterviewApplication.java     entry point
  model/        AppUser, InterviewSession, QuestionAnswer   (JPA entities = your SQL tables)
  repo/         Spring Data JPA repositories (no SQL to write)
  security/     JwtService, JwtAuthFilter, SecurityConfig
  service/      GeminiService   -> calls the Gemini REST API
                AuthService     -> register / login
                InterviewService-> the brain: prompts, scoring, sessions
  web/          AuthController, InterviewController, Dtos, ApiExceptionHandler
src/main/resources/
  application.properties
  static/       index.html, styles.css, app.js  (the UI)
```

### The API

| Method | Path | What it does |
|---|---|---|
| POST | `/api/auth/register` | create account, returns JWT |
| POST | `/api/auth/login` | sign in, returns JWT |
| GET | `/api/interview/me` | current user + topic list |
| POST | `/api/interview/start` | new session + first question |
| POST | `/api/interview/{id}/answer` | score the answer, return next question |
| GET | `/api/interview/history` | all past sessions |
| GET | `/api/interview/{id}` | full transcript of one session |

All `/api/interview/**` calls need the header `Authorization: Bearer <token>`.

### The two prompts that do the AI work

Both live in `InterviewService.java`:

- `generateQuestion(...)` — asks Gemini for one question on the topic at the
  chosen difficulty, and passes the already-asked questions so it never repeats.
- `evaluationPrompt(...)` — sends the question + the candidate's answer and asks
  for `{"score": 0-10, "feedback": "...", "model_answer": "..."}`.

Gemini is asked for `responseMimeType: application/json`, so the reply is easy
to parse. That's all the "prompt engineering" this project needs — tweak the
wording, restart, see what changes.

---

## 6. Suggested build order (so you understand every piece)

1. Run it as-is with H2 and make one round of interview work.
2. Read `GeminiService` and change a prompt — see how the feedback changes.
3. Switch to MySQL and look at the three tables after a round.
4. Add one feature yourself. Good candidates, in rising difficulty:
   - Show the full transcript of a past session (the `GET /api/interview/{id}` endpoint already exists — the UI doesn't use it yet).
   - Let the user resume an unfinished interview.
   - Add a "weak topics" screen: average score grouped by topic (`GROUP BY topic` — good SQL to show off).
   - Export a session as PDF.
   - Voice answers with the browser's Web Speech API.

## 7. Resume line you can honestly write

> **AI Mock Interview Bot** — Java, Spring Boot, MySQL, JWT, Google Gemini API.
> Built a full-stack interview practice app that generates topic-wise technical
> questions via the Gemini API, evaluates free-text answers with structured
> JSON scoring and feedback, and persists per-user sessions, scores and
> transcripts in MySQL with stateless JWT authentication.

## 8. Troubleshooting

- **"GEMINI_API_KEY is not set"** → the environment variable isn't reaching the app. In IntelliJ, set it in the run configuration, not the terminal.
- **400 / 429 from Gemini** → free-tier rate limit or an invalid key. Wait a minute or regenerate the key.
- **"Gemini did not return valid JSON"** → rare; the retry is just clicking Send again. The prompt already requests JSON.
- **Login works then every call fails** → `JWT_SECRET` changed between restarts; old tokens die. Sign in again.
- **MySQL access denied** → wrong password in `application.properties`.
