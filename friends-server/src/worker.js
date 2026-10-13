// Friends-Server als Cloudflare Worker. Die Daten liegen in einem Durable Object mit eingebautem SQLite
// (eine Instanz "main"), siehe deploy.mjs.
import { DurableObject } from "cloudflare:workers";
import { createApi } from "./api.js";
import { SCHEMA, MIGRATIONS } from "./schema.js";

export class FriendsStore extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    const sql = ctx.storage.sql;
    for (const statement of SCHEMA.split(";").map((s) => s.trim()).filter(Boolean)) sql.exec(statement);
    for (const statement of MIGRATIONS) {
      try {
        sql.exec(statement);
      } catch {
        // schon vorhanden
      }
    }
    const db = {
      all: async (query, params = []) => sql.exec(query, ...params).toArray(),
      get: async (query, params = []) => sql.exec(query, ...params).toArray()[0] || null,
      run: async (query, params = []) => sql.exec(query, ...params),
    };
    this.api = createApi(db, { allowOffline: env.ALLOW_OFFLINE === "1", adminKey: env.ADMIN_KEY, log: (line) => console.log(line) });
  }

  async fetch(request) {
    return this.api(request);
  }
}

export default {
  async fetch(request, env) {
    return env.FRIENDS.get(env.FRIENDS.idFromName("main")).fetch(request);
  },
};
