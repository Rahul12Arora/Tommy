import { hashPassword } from "../src/lib/password.js";

/**
 * `npm run hash-password`: prompts for the admin password without echoing it and prints the line for .env
 * (ARCHITECTURE §14.11, D-138). Piped input (`… | npm run hash-password`) is read as-is, for scripts.
 */
const MIN_LENGTH = 12;

const password = process.stdin.isTTY ? await readHidden("Password: ") : (await readAll()).replace(/\r?\n$/, "");
if (password.length < MIN_LENGTH) {
  console.error(`The password must be at least ${MIN_LENGTH} characters.`);
  process.exit(1);
}
console.log(`ADMIN_PASSWORD_HASH=${await hashPassword(password)}`);

function readHidden(prompt) {
  process.stderr.write(prompt);
  process.stdin.setRawMode(true);
  process.stdin.setEncoding("utf8");
  return new Promise((resolve) => {
    let typed = "";
    const onData = (chunk) => {
      for (const char of chunk) {
        if (char === "\u0003") process.exit(130); // Ctrl+C
        if (char === "\r" || char === "\n") {
          process.stdin.setRawMode(false);
          process.stdin.pause();
          process.stdin.off("data", onData);
          process.stderr.write("\n");
          resolve(typed);
          return;
        }
        typed = char === "\u007f" ? typed.slice(0, -1) : typed + char; // \u007f = Backspace
      }
    };
    process.stdin.on("data", onData);
  });
}

async function readAll() {
  let text = "";
  for await (const chunk of process.stdin) text += chunk;
  return text;
}
