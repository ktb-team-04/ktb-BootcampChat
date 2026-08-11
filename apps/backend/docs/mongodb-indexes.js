// Run with:
// mongosh "mongodb://<host>:27017/bootcamp-chat" apps/backend/docs/mongodb-indexes.js

const database = db.getSiblingDB("bootcamp-chat");

database.users.createIndex(
  { email: 1 },
  { name: "email_unique_idx", unique: true }
);

database.messages.createIndex(
  { room: 1, timestamp: -1 },
  { name: "room_timestamp_idx" }
);

database.sessions.createIndex(
  { userId: 1, sessionId: 1 },
  { name: "userId_sessionId_idx", unique: true }
);

database.sessions.createIndex(
  { expiresAt: 1 },
  { name: "expiresAt_ttl_idx", expireAfterSeconds: 1800 }
);

database.rate_limits.createIndex(
  { clientId: 1 },
  { name: "clientId_unique_idx", unique: true }
);

database.rate_limits.createIndex(
  { expiresAt: 1 },
  { name: "rateLimit_expiresAt_ttl_idx", expireAfterSeconds: 0 }
);
