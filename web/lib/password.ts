import { pbkdf2, randomBytes, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";

const deriveKey = promisify(pbkdf2);
const ITERATIONS = 210_000;
const KEY_BYTES = 64;

export async function hashPassword(password: string, saltHex = randomBytes(16).toString("hex")) {
  const derived = await deriveKey(password, Buffer.from(saltHex, "hex"), ITERATIONS, KEY_BYTES, "sha512");
  return { salt: saltHex, hash: derived.toString("hex") };
}

export async function verifyPassword(password: string, saltHex: string, hashHex: string) {
  const { hash } = await hashPassword(password, saltHex);
  const actual = Buffer.from(hash, "hex");
  const expected = Buffer.from(hashHex, "hex");
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}
