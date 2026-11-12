# LAN Chat Web Client

This is an optional, mobile-friendly browser client for the LAN Chat project. It is a separate deployment from the Java Swing coursework app. It uses Next.js route handlers, PostgreSQL, password hashing, signed HTTP-only sessions, and periodic HTTP polling for messages and presence.

## Local development

Requirements: Node.js 20.9 or newer and a PostgreSQL database. Neon is convenient for local and Vercel use.

1. Copy `.env.example` to `.env.local` and fill in `DATABASE_URL` and a private `SESSION_SECRET` of at least 32 bytes.
2. Install packages and create the database tables:

   ```powershell
   npm install
   npm run db:setup
   ```

3. Start the app:

   ```powershell
   npm run dev
   ```

4. Open `http://localhost:3000`, register a user, and sign in.

To create a local session secret in PowerShell, run:

```powershell
node -e "console.log(require('node:crypto').randomBytes(48).toString('base64url'))"
```

## Deploy to Vercel

1. Create a Vercel project from this repository and set **Root Directory** to `web`.
2. Provision a PostgreSQL database (for example, Neon through the Vercel Marketplace) and add `DATABASE_URL` to the Vercel project's Production, Preview, and Development environment variables.
3. Generate a strong random value for `SESSION_SECRET` and add it to those same Vercel environments. Keep it private and do not commit it.
4. Set the same `DATABASE_URL` in your local `web/.env.local`, then run `npm run db:setup` from `web/` once to create the tables.
5. Deploy from the Vercel dashboard or with the Vercel CLI from `web/`.

Vercel's filesystem is not durable storage, so chat data is kept in PostgreSQL. The app polls the API for updates and does not need a long-lived WebSocket connection.

## Relationship to the Java project

The Java Swing app and this browser app use separate server implementations and databases. The Java app continues to demonstrate the assigned Java modules (Swing, sockets, threads, JDBC, and SQLite). The web edition is an optional deployment path for phone browsers; messages do not synchronize between the two editions unless a shared backend is built later.
