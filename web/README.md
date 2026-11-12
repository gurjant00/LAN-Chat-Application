# LAN Chat Web Client

This is the optional, mobile-friendly browser client for the LAN Chat project. The production site is [lan-chat-application-sage.vercel.app](https://lan-chat-application-sage.vercel.app). It is separate from the Java Swing coursework app and uses Next.js route handlers, Neon PostgreSQL, salted password hashes, signed HTTP-only sessions, and periodic HTTP polling for messages and presence.

## Local development

Requirements: Node.js 20.9 or newer and a PostgreSQL database. Neon is used by the production deployment and also works for local development.

1. From the `web` directory, copy `.env.example` to `.env.local` and set `DATABASE_URL` to your PostgreSQL connection string. Set `SESSION_SECRET` to a private random value of at least 32 bytes.
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

To generate a local session secret in PowerShell, run:

```powershell
node -e "console.log(require('node:crypto').randomBytes(48).toString('base64url'))"
```

## Deploy to Vercel

1. Import this repository into Vercel and set **Root Directory** to `web`.
2. Provision a PostgreSQL database, such as Neon through Vercel Marketplace, and link it to the Vercel project. Confirm `DATABASE_URL` is available in the environments you plan to deploy.
3. Generate a strong random value for `SESSION_SECRET` and add it to each Vercel environment you use (Production, Preview, and Development). Keep it private and never commit it.
4. Create the schema once for the target database. Locally, set `DATABASE_URL` in `web/.env.local` and run `npm run db:setup` from `web/`; alternatively, execute [`db/schema.sql`](db/schema.sql) in the database's SQL editor.
5. Deploy from the Vercel dashboard or with the Vercel CLI. After changing environment variables, redeploy so the deployment receives the new values.

The production site linked above is connected to Neon. Its `users`, `messages`, and `presence` tables have been created from `db/schema.sql`.

Vercel's filesystem is not durable storage, so chat data is kept in PostgreSQL. The app polls its API for updates and does not require a long-lived WebSocket connection.

## Relationship to the Java project

The Java Swing app and this browser app use separate server implementations and databases. The Java app continues to demonstrate the assigned Java modules (Swing, sockets, threads, JDBC, and SQLite). The web edition is an optional deployment path for phone browsers; messages do not synchronize between the two editions unless a shared backend is built later.
