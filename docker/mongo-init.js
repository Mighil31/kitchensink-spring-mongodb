// Runs only when the volume is first created: changing .env later doesn't change these users.
// The app gets readWrite on its own database only; the root user (MONGO_INITDB_ROOT_*) is for admin.
const dbName = process.env.MONGO_APP_DB;
db.getSiblingDB(dbName).createUser({
  user: process.env.MONGO_APP_USER,
  pwd: process.env.MONGO_APP_PASSWORD,
  roles: [{ role: "readWrite", db: dbName }],
});
