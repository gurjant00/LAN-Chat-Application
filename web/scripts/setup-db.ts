import "dotenv/config";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { neon } from "@neondatabase/serverless";

async function main() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    throw new Error("Set DATABASE_URL in web/.env.local before running db:setup.");
  }

  const sql = neon(databaseUrl);
  const schema = await readFile(resolve(process.cwd(), "db/schema.sql"), "utf8");
  const statements = schema.split(";").map((statement) => statement.trim()).filter(Boolean);
  for (const statement of statements) await sql.query(statement);
  console.log(`Database schema ready (${statements.length} statements).`);
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : "Database setup failed.");
  process.exitCode = 1;
});
