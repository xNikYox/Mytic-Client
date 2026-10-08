// Microsoft-Login für Minecraft (OAuth mit PKCE → Xbox Live → XSTS → Minecraft) und Offline-Konten.
const crypto = require("node:crypto");
const { fetchRetry } = require("./download");

const AUTHORIZE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const REDIRECT_URI = "https://login.microsoftonline.com/common/oauth2/nativeclient";
const SCOPE = "XboxLive.signin offline_access";

const XSTS_ERRORS = {
  2148916227: "Dieses Konto ist von Xbox gesperrt.",
  2148916233: "Dieses Microsoft-Konto hat noch kein Xbox-Profil. Melde dich einmal auf xbox.com an und versuche es erneut.",
  2148916235: "Xbox Live ist in deinem Land nicht verfügbar.",
  2148916236: "Für dieses Konto ist eine Altersbestätigung (Südkorea) nötig.",
  2148916237: "Für dieses Konto ist eine Altersbestätigung (Südkorea) nötig.",
  2148916238: "Kinderkonto: Ein Erwachsener muss das Konto zu einer Microsoft-Familie hinzufügen.",
};

function pkce() {
  const verifier = crypto.randomBytes(32).toString("base64url");
  const challenge = crypto.createHash("sha256").update(verifier).digest("base64url");
  return { verifier, challenge };
}

function authorizeUrl(clientId, challenge, state) {
  const params = new URLSearchParams({
    client_id: clientId,
    response_type: "code",
    redirect_uri: REDIRECT_URI,
    scope: SCOPE,
    code_challenge: challenge,
    code_challenge_method: "S256",
    prompt: "select_account",
    state,
  });
  return `${AUTHORIZE}?${params}`;
}

async function postForm(url, form) {
  const response = await fetchRetry(url, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams(form) });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.error_description || body.error || `HTTP ${response.status}`);
  return body;
}

async function postJson(url, json, extraHeaders = {}) {
  const response = await fetchRetry(url, { method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json", ...extraHeaders }, body: JSON.stringify(json) });
  const text = await response.text();
  let body = {};
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    body = { raw: text };
  }
  return { ok: response.ok, status: response.status, body };
}

async function exchangeCode(clientId, code, verifier) {
  return postForm(TOKEN, { client_id: clientId, grant_type: "authorization_code", code, redirect_uri: REDIRECT_URI, code_verifier: verifier, scope: SCOPE });
}

async function refreshMicrosoft(clientId, refreshToken) {
  return postForm(TOKEN, { client_id: clientId, grant_type: "refresh_token", refresh_token: refreshToken, redirect_uri: REDIRECT_URI, scope: SCOPE });
}

/** Microsoft-Token → Minecraft-Token und Profil. */
async function minecraftLogin(msAccessToken) {
  const xbl = await postJson("https://user.auth.xboxlive.com/user/authenticate", {
    Properties: { AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com", RpsTicket: `d=${msAccessToken}` },
    RelyingParty: "http://auth.xboxlive.com",
    TokenType: "JWT",
  });
  if (!xbl.ok) throw new Error(`Xbox-Anmeldung fehlgeschlagen (HTTP ${xbl.status})`);
  const uhs = xbl.body.DisplayClaims.xui[0].uhs;

  const xsts = await postJson("https://xsts.auth.xboxlive.com/xsts/authorize", {
    Properties: { SandboxId: "RETAIL", UserTokens: [xbl.body.Token] },
    RelyingParty: "rp://api.minecraftservices.com/",
    TokenType: "JWT",
  });
  if (!xsts.ok) throw new Error(XSTS_ERRORS[xsts.body.XErr] || `Xbox-Freigabe fehlgeschlagen (HTTP ${xsts.status})`);

  const mc = await postJson("https://api.minecraftservices.com/authentication/login_with_xbox", { identityToken: `XBL3.0 x=${uhs};${xsts.body.Token}` });
  if (!mc.ok) {
    if (mc.status === 403) {
      throw new Error("Minecraft hat die Anmeldung abgelehnt. Wahrscheinlich ist deine Client-ID noch nicht von Mojang freigeschaltet (siehe Anleitung).");
    }
    throw new Error(`Minecraft-Anmeldung fehlgeschlagen (HTTP ${mc.status})`);
  }

  const profileResponse = await fetchRetry("https://api.minecraftservices.com/minecraft/profile", { headers: { Authorization: `Bearer ${mc.body.access_token}` } });
  if (profileResponse.status === 404) throw new Error("Dieses Konto besitzt Minecraft: Java Edition nicht.");
  if (!profileResponse.ok) throw new Error(`Profil konnte nicht geladen werden (HTTP ${profileResponse.status})`);
  const profile = await profileResponse.json();
  return {
    type: "microsoft",
    name: profile.name,
    uuid: profile.id,
    accessToken: mc.body.access_token,
    expiresAt: Date.now() + (mc.body.expires_in || 86400) * 1000,
    xuid: decodeXuid(xsts.body.Token) || "",
  };
}

function decodeXuid(jwt) {
  try {
    const payload = JSON.parse(Buffer.from(jwt.split(".")[1], "base64url").toString());
    return payload.xid || "";
  } catch {
    return "";
  }
}

/** UUID wie bei Offline-Servern: v3 aus "OfflinePlayer:<Name>". */
function offlineUuid(name) {
  const hash = crypto.createHash("md5").update(`OfflinePlayer:${name}`).digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const hex = hash.toString("hex");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function offlineAccount(name) {
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) throw new Error("Name: 3–16 Zeichen, nur Buchstaben, Zahlen und _");
  return { type: "offline", name, uuid: offlineUuid(name), accessToken: "0", xuid: "" };
}

module.exports = { pkce, authorizeUrl, exchangeCode, refreshMicrosoft, minecraftLogin, offlineAccount, offlineUuid, REDIRECT_URI };
