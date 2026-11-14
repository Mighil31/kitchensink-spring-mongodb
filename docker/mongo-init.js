// Runs once, when the mongo volume is first created.
// The app gets its own user with readWrite on its database only (least privilege);
// the root user from MONGO_INITDB_ROOT_* is for administration.
const dbName = process.env.MONGO_APP_DB;
db.getSiblingDB(dbName).createUser({
  user: process.env.MONGO_APP_USER,
  pwd: process.env.MONGO_APP_PASSWORD,
  roles: [{ role: "readWrite", db: dbName }],
});
