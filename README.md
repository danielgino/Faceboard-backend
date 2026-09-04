

# Faceboard

Faceboard is a full-stack social network built with a React frontend and a Spring Boot backend. It covers the core of a social app — posts, friendships, stories, notifications, and real-time one-to-one chat over WebSocket — backed by MySQL and Cloudinary for media storage. It also ships with a public, read-only demo account so anyone can explore it without creating a real account.

This repository is the backend — REST + WebSocket API, business logic and security. The React client lives in `Faceboard-frontend`.

## Live Demo
<img width="651" height="361" alt="FACEBOARD" src="https://github.com/user-attachments/assets/7ea74fcd-e0d9-4377-b358-3ac74f903be8" />


Frontend: [faceboard-frontend.vercel.app](https://faceboard-frontend.vercel.app)
<!-- TODO: add a few screenshots (feed, chat, demo banner) here -->

Click **Demo** on the login page for a temporary, shared demo account — no signup required. It's read-only: the backend rejects write requests regardless of what the UI shows. See [Demo Mode](#demo-mode) for details.

## Features

**Social**
- Text and image posts (up to 4 images per post), with editing and deleting
- Likes and comments
- Feed with infinite scroll, plus a per-user photo gallery
- Post links that can be copied or shared directly into a chat

**Friends**
- User search and suggested friends
- Friend requests — send, accept, decline, cancel — and unfriending
- Friends list

**Messaging & Notifications**
- Real-time one-to-one chat with persisted history and read receipts
- Real-time notifications for friend requests, acceptances, likes and comments

**Stories** — upload and view, with automatic 24-hour expiry

**Account** — profile editing, avatar upload, password change, email-based password reset

**UI** — responsive layout with separate desktop and mobile navigation

## Technical Highlights

- **JWT-authenticated STOMP chat** — WebSocket connections require a valid JWT on CONNECT, with per-user topic authorization and persisted read receipts.
- **Backend-enforced demo mode** — read-only restrictions on the demo account are enforced server-side (endpoint filtering, service-layer checks, WebSocket rejection), not just disabled UI buttons.
- **JWT invalidation on password change** — tokens embed a fingerprint of the current password hash, so a password change or reset invalidates every prior token instantly, with no revocation list needed.
- **Deterministic friend suggestions** — keyset pagination with a daily-rotating per-user seed, excluding existing and pending friends at the query level, instead of `ORDER BY RAND()`.
- **Decoder-based image validation** — uploads are decoded and checked against their declared content type and pixel limits before reaching Cloudinary.
- **Atomic password reset** — reset tokens are hashed, time-limited, and consumed atomically to close the race between two concurrent reset attempts.
- **Commit-aware broadcasts** — WebSocket events for new posts and notifications fire only after the database transaction that created them commits.

## Tech Stack

**Frontend** — React 18, React Router, Tailwind CSS, styled-components, STOMP.js (`@stomp/stompjs`) for the WebSocket client, ChatScope UI kit for chat, Jest / React Testing Library.

**Backend** — Java 17, Spring Boot 3.4, Spring Security, Spring Data JPA/Hibernate, Spring WebSocket/STOMP, MySQL, Flyway, JWT (jjwt), MapStruct, Cloudinary SDK.

**Infrastructure** — Vercel (frontend hosting), Render (backend hosting), Aiven MySQL (database), GitHub Actions (build/test on push and PR).

## Architecture

```
React (frontend)
   │  REST over fetch (JWT in Authorization header)
   ▼
Spring Boot (backend) ──► Cloudinary (media storage)
   │
   ▼
MySQL (schema managed by Flyway)

React (frontend) ◄──── STOMP over WebSocket ────► Spring Boot (backend)
```

**Frontend** — React with context-based state (no Redux), a centralized `fetchWithAuth` helper for authenticated requests, and a dedicated WebSocket/STOMP client.

**Backend** — Controller → service → repository layering, Spring Security for authentication, JPA/MySQL with the schema versioned through Flyway, and Cloudinary for media storage.

## Security

Security-related implementation includes:

- BCrypt password hashing
- JWT authentication for protected API requests
- Token invalidation on password change (fingerprint-based)
- Authenticated identity is taken from the security context for protected mutations, with ownership checks on posts, comments, likes, friendships and profile actions.
- Authenticated, per-user STOMP/WebSocket access
- Hashed, expiring, single-use password reset tokens
- Image upload validation (content type, decode check, size limits)
- Backend-enforced demo account restrictions, with rate limiting on demo login and demo API calls

## Demo Mode

The demo account is a single shared user, seeded on the backend and reachable only via `/auth/demo` (no password required). Tokens are short-lived (15 minutes) and rate-limited per IP.

Read access — feed, profiles, search, notifications, stories — works normally. Write actions (posting, liking, commenting, friending, profile edits, uploads) and chat are blocked; chat is blocked at the WebSocket layer, since a demo session never opens a STOMP connection.

Most demo content (posts, comments, likes, friends, chat threads, notifications) is real seeded data. Stories and the gallery use static bundled assets instead.

## Running Locally

### Backend
Requirements: Java 17, Maven (or the included wrapper), a MySQL instance.

```bash
git clone https://github.com/danielgino/Faceboard-backend.git
cd Faceboard-backend
# set the environment variables listed below, pointing DB_URL at your local MySQL instance
./mvnw spring-boot:run      # on Windows: mvnw.cmd spring-boot:run
```

### Frontend
Requirements: Node.js and npm.

```bash
git clone https://github.com/danielgino/Faceboard-frontend.git
cd Faceboard-frontend
npm install
# set REACT_APP_API_URL and REACT_APP_WS_URL to point at your local backend
npm start
```

## Environment Variables

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL for the MySQL database |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Secret used to sign/verify JWTs (required — the app won't start without it) |
| `CLOUDINARY_NAME` | Cloudinary cloud name |
| `CLOUDINARY_API_KEY` | Cloudinary API key |
| `CLOUDINARY_API_SECRET` | Cloudinary API secret |
| `SMTP_EMAIL` | Sender address for password-reset emails |
| `SMTP_PASSWORD` | SMTP credentials for the mail account above |
| `DEMO_MODE_ENABLED` | Turns the public demo account on/off |
| `FACEBOARD_CREATOR_USER_ID` | User ID used to reserve a slot in friend suggestions |
| `FRONTEND_BASE_URL` | Frontend base URL used when generating application links (e.g. password reset links) |
| `PORT` | Port the server listens on (optional, defaults to 8080) |
| `REACT_APP_API_URL` | Frontend: base URL of the backend REST API |
| `REACT_APP_WS_URL` | Frontend: WebSocket URL of the backend (`/ws` endpoint) |

## Testing and CI

**Backend** — Tests cover authentication, demo-mode restrictions, upload validation, password reset, and friendship logic. CI runs the suite against a real MySQL instance.

**Frontend** — Jest and React Testing Library cover contexts, hooks, utilities, demo-mode behavior, and chat/WebSocket handling. No end-to-end tests (no Cypress/Playwright).

**GitHub Actions** — Builds and tests run on every push and pull request to `main` for both repos, including a production build. Render and Vercel deploy separately through their own Git integration.

## Deployment

- Frontend: Vercel
- Backend: Render
- Database: Aiven MySQL
- Media: Cloudinary

## Project Structure

Backend (`src/main/java/.../apimywebsite`):
```
controller/       REST controllers
service/          Business logic
repository/       Spring Data JPA repositories
model/            JPA entities
dto/              Request/response DTOs
mapper/           MapStruct mappers between entities and DTOs
configuration/    Security, WebSocket, password policy config
util/             JWT utilities, demo-mode enforcement, exception handling, rate limiting
```

Frontend (`src/`):
```
pages/            Route-level page components
components/       Reusable and domain components (posts, profile, chat, etc.)
context/          Auth, friendship, chat, notification, story providers
hooks/            Custom hooks (search debounce, suggestions, safety tip, etc.)
service/          WebSocket message handling
utils/            fetchWithAuth, validation, shared constants
```

## Author

Built by Daniel Gino as a portfolio/learning project.

- [LinkedIn](https://www.linkedin.com/in/daniel-gino-2b6350345/)
- [GitHub](https://github.com/danielgino)
- [Facebook](https://www.facebook.com/Daniegino)
- [Instagram](https://www.instagram.com/daniel_gino)
