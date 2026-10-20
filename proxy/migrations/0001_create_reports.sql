-- "Report this quest" storage (ARCH §3.8): the only thing the proxy stores. No install ID column, by design.
CREATE TABLE IF NOT EXISTS reports (
  id TEXT PRIMARY KEY,
  promptVersion TEXT NOT NULL,
  reason TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  createdAtMs INTEGER NOT NULL,
  expiresAtMs INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reports_expires ON reports (expiresAtMs);
