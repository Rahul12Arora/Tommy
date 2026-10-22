/** An error our own code throws on purpose; the error handler sends it as the standard shape (ARCHITECTURE §7). */
export class HttpError extends Error {
  constructor(statusCode, code, message, details) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
    this.details = details;
  }
}
