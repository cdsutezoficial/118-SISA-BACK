#!/usr/bin/env node
/**
 * Banco de pruebas manual para el flujo OAuth 2.0 + PKCE de Llave Morelos.
 *
 * Genera el par `code_verifier`/`code_challenge` y el `state` que tu back
 * fabrica en `AuthenticateWithLlaveMxUseCaseImpl.start()`, para que puedas
 * recorrer el flujo a mano en el navegador sin levantar el backend.
 *
 * La construcción de la URL imita a `LlaveMxOAuthAdapter.buildAuthorizationUrl`
 * y el canje de tokens a `exchangeAuthorizationCode`, así que lo que pruebes
 * aquí es exactamente lo que mandaría el back.
 *
 * Uso:
 *   node tools/llave-probe.mjs init [--redirect <url>]
 *   node tools/llave-probe.mjs redeem "<url de retorno con ?code=...>"
 *   node tools/llave-probe.mjs profile
 *   node tools/llave-probe.mjs status
 *
 * El estado (verifier y tokens) vive en `.llave-probe.json`, que está en el
 * .gitignore: es material sensible.
 */

import { createHash, randomBytes } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const STATE_FILE = join(ROOT, '.llave-probe.json')

const DEFAULTS = {
  authorizeUrl: 'https://simple.morelos.gob.mx/oauth/authorize',
  tokenUrl: 'https://hub.morelos.gob.mx/oauth/token',
  refreshUrl: 'https://hub.morelos.gob.mx/oauth/token/refresh',
  entityUrl: '',
  scope: 'read:profile receive:notifications accept:tos accept:privacy',
  prompt: 'consent',
}

// ---------------------------------------------------------------------------
// .env (misma fuente que usa Spring vía spring.config.import)
// ---------------------------------------------------------------------------

function loadEnv() {
  const file = join(ROOT, '.env')
  if (!existsSync(file)) return {}
  const out = {}
  for (const raw of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || line.startsWith('#')) continue
    const eq = line.indexOf('=')
    if (eq < 0) continue
    out[line.slice(0, eq).trim()] = line.slice(eq + 1).trim()
  }
  return out
}

const ENV = loadEnv()

const CFG = {
  authorizeUrl: ENV.LLAVE_AUTHORIZE_URL || DEFAULTS.authorizeUrl,
  tokenUrl: ENV.LLAVE_TOKEN_URL || DEFAULTS.tokenUrl,
  entityUrl: ENV.LLAVE_ENTITY_URL || DEFAULTS.entityUrl,
  clientId: ENV.LLAVE_CLIENT_ID || '',
  clientSecret: ENV.LLAVE_CLIENT_SECRET || '',
  redirectUri: ENV.LLAVE_REDIRECT_URI || '',
  scope: ENV.LLAVE_SCOPE || DEFAULTS.scope,
  prompt: ENV.LLAVE_PROMPT || DEFAULTS.prompt,
}

// ---------------------------------------------------------------------------
// PKCE — mismos tamaños que LlaveMxPkceGenerator
// ---------------------------------------------------------------------------

function base64url(bytes) {  return Buffer.from(bytes).toString('base64url')
}

function newState() {
  return base64url(randomBytes(24)) // 24 bytes → 32 chars
}

function newCodeVerifier() {
  return base64url(randomBytes(32)) // 32 bytes → 43 chars (RFC 7636 mínimo)
}

function challengeOf(verifier) {
  return createHash('sha256').update(verifier, 'ascii').digest('base64url')
}

// ---------------------------------------------------------------------------
// Estado persistente
// ---------------------------------------------------------------------------

function readState() {
  if (!existsSync(STATE_FILE)) return null
  return JSON.parse(readFileSync(STATE_FILE, 'utf8'))
}

function writeState(state) {
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2) + '\n')
}

function requireConfig() {
  const missing = ['clientId', 'clientSecret', 'redirectUri'].filter((k) => !CFG[k])
  if (missing.length) {
    console.error(`Falta configuracion en .env: ${missing.join(', ')}`)
    process.exit(1)
  }
}

// ---------------------------------------------------------------------------
// Comandos
// ---------------------------------------------------------------------------

function cmdInit(args) {
  requireConfig()

  const explicitRedirect = argValue(args, '--redirect')
  const redirect = explicitRedirect || CFG.redirectUri

  const state = newState()
  const verifier = newCodeVerifier()
  const challenge = challengeOf(verifier)

  writeState({
    state,
    codeVerifier: verifier,
    redirectUri: redirect,
    startedAt: new Date().toISOString(),
    code: null,
    accessToken: null,
    refreshToken: null,
    expiresAt: null,
  })

  const url = new URL(CFG.authorizeUrl)
  url.searchParams.set('client_id', CFG.clientId)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('scope', CFG.scope)
  url.searchParams.set('state', state)
  url.searchParams.set('code_challenge', challenge)
  url.searchParams.set('code_challenge_method', 'S256')
  url.searchParams.set('prompt', CFG.prompt)
  url.searchParams.set('redirect_uri', redirect)

  console.log('PKCE generado y guardado en .llave-probe.json\n')
  console.log('  state         ' + state)
  console.log('  code_verifier ' + verifier)
  console.log('  code_challenge' + ' ' + challenge)
  console.log('  redirect_uri  ' + redirect + (explicitRedirect ? '  (override)' : ''))
  console.log('')
  console.log('Abre esta URL en el navegador:\n')
  console.log(url.toString())
  console.log('')
  console.log('Despues de autenticarte, el navegador caera en el redirect_uri.')
  console.log('Aunque la pagina falle, copia la URL COMPLETA de la barra de')
  console.log('direcciones (trae ?code=...&state=...) y corre:\n')
  console.log('  node tools/llave-probe.mjs redeem "PEGA_LA_URL_AQUI"')
}

async function cmdRedeem(args) {
  requireConfig()
  const st = readState()
  if (!st) {
    console.error('No hay estado. Corre primero: node tools/llave-probe.mjs init')
    process.exit(1)
  }

  const pasted = args[0]
  if (!pasted) {
    console.error('Pega la URL de retorno como argumento.')
    process.exit(1)
  }

  let parsed
  try {
    parsed = new URL(pasted.trim())
  } catch {
    console.error('Esa no es una URL valida.')
    process.exit(1)
  }

  const providerError = parsed.searchParams.get('error')
  if (providerError) {
    console.error('Llave Morelos devolvio un error: ' + providerError)
    const desc = parsed.searchParams.get('error_description')
    if (desc) console.error('  ' + desc)
    process.exit(1)
  }

  const code = parsed.searchParams.get('code')
  const returnedState = parsed.searchParams.get('state')

  if (!code) {
    console.error('La URL no trae ?code=')
    process.exit(1)
  }
  if (returnedState !== st.state) {
    console.error('El state no coincide (posible CSRF o URL de otra tanda).')
    console.error('  esperado ' + st.state)
    console.error('  recibido ' + returnedState)
    process.exit(1)
  }

  // El redirect_uri tiene que ser byte-identico al del authorize, por eso se
  // guarda en el estado en vez de re-leerse del .env.
  const form = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: CFG.clientId,
    client_secret: CFG.clientSecret,
    code,
    redirect_uri: st.redirectUri,
    code_verifier: st.codeVerifier,
  })

  console.log('Canjeando el authorization code...\n')

  const res = await fetch(CFG.tokenUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: form.toString(),
  })

  const body = await res.json().catch(() => ({}))

  if (!res.ok) {
    console.error('HTTP ' + res.status)
    console.error(JSON.stringify(body, null, 2))
    if (body.error === 'invalid_grant') {
      console.error('\ninvalid_grant = codigo ya usado, expirado o verifier no coincide.')
      console.error('Vuelve a correr: node tools/llave-probe.mjs init')
    } else if (body.error === 'invalid_redirect_uri') {
      console.error('\nEl redirect_uri no coincide con el registrado en el hub.')
      console.error('Usado: ' + st.redirectUri)
    } else if (body.error === 'invalid_client') {
      console.error('\nclient_id / client_secret no validos.')
    }
    process.exit(1)
  }

  st.code = code
  st.accessToken = body.access_token
  st.refreshToken = body.refresh_token ?? null
  st.expiresAt = body.expires_in ? new Date(Date.now() + body.expires_in * 1000).toISOString() : null
  writeState(st)

  console.log('OK. Tokens guardados en .llave-probe.json\n')
  console.log('  token_type   ' + (body.token_type || 'Bearer'))
  console.log('  expires_in   ' + (body.expires_in ?? 'n/d'))
  console.log('  access_token ' + String(st.accessToken).slice(0, 40) + '...')
  console.log('')
  console.log('Ahora pide el perfil:\n')
  console.log('  node tools/llave-probe.mjs profile')
}

async function cmdProfile() {
  const st = readState()
  if (!st || !st.accessToken) {
    console.error('No hay access_token. Corre antes: node tools/llave-probe.mjs redeem "<url>"')
    process.exit(1)
  }
  if (!CFG.entityUrl) {
    console.error('LLAVE_ENTITY_URL esta vacio en .env.')
    process.exit(1)
  }

  console.log('GET ' + CFG.entityUrl + '\n')

  const res = await fetch(CFG.entityUrl, {
    headers: { Authorization: 'Bearer ' + st.accessToken },
  })
  const text = await res.text()

  if (!res.ok) {
    console.error('HTTP ' + res.status)
    console.error(text)
    if (res.status === 401) console.error('\n401 = access_token vencido o no valido para este recurso.')
    process.exit(1)
  }

  try {
    console.log(JSON.stringify(JSON.parse(text), null, 2))
  } catch {
    console.log(text)
  }

  console.log('\nEsos son los campos que LlaveMX entrega. El back los normaliza en')
  console.log('LlaveMxOAuthAdapter.prefillOf() -> LlaveMxPrefill (dd/MM/yyyy, sexo).')
}

function cmdStatus() {
  const st = readState()
  if (!st) {
    console.log('Sin estado. Corre: node tools/llave-probe.mjs init')
    return
  }
  console.log('state         ' + st.state)
  console.log('redirect_uri  ' + st.redirectUri)
  console.log('iniciado      ' + st.startedAt)
  console.log('code          ' + (st.code ? 'si (canjeado)' : 'no'))
  console.log('access_token  ' + (st.accessToken ? 'si' : 'no'))
  console.log('expira        ' + (st.expiresAt ?? 'n/d'))
}

// ---------------------------------------------------------------------------

function argValue(args, flag) {
  const i = args.indexOf(flag)
  return i >= 0 ? args[i + 1] : null
}

const [command, ...rest] = process.argv.slice(2)

switch (command) {
  case 'init':
    cmdInit(rest)
    break
  case 'redeem':
    await cmdRedeem(rest)
    break
  case 'profile':
    await cmdProfile()
    break
  case 'status':
    cmdStatus()
    break
  default:
    console.log(`
Banco de pruebas manual de Llave Morelos (PKCE)

  node tools/llave-probe.mjs init [--redirect <url>]
      Genera state/verifier/challenge e imprime la URL de autorizacion.

  node tools/llave-probe.mjs redeem "<url de retorno>"
      Canjea el ?code= de la URL por tokens. Verifica el state.

  node tools/llave-probe.mjs profile
      Consulta el perfil con el access_token (LLAVE_ENTITY_URL).

  node tools/llave-probe.mjs status
      Muestra el estado guardado.

Lee client_id, client_secret y redirect_uri de .env.
`)
}
