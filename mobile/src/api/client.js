import { Capacitor } from '@capacitor/core';

const REQUEST_TIMEOUT_MS = 15000;
let accessToken = null;

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export function getApiBaseUrl() {
  const configuredUrl = import.meta.env.VITE_API_BASE_URL?.trim();
  if (configuredUrl) {
    return configuredUrl.replace(/\/+$/, '');
  }

  if (Capacitor.isNativePlatform()) {
    if (Capacitor.getPlatform() === 'android') {
      return 'http://10.0.2.2:8080';
    }
    throw new ApiError(
      'Configurez VITE_API_BASE_URL avec une adresse accessible depuis cet appareil.'
    );
  }

  const hostname = globalThis.location?.hostname || '127.0.0.1';
  const protocol = globalThis.location?.protocol === 'https:' ? 'https:' : 'http:';
  return `${protocol}//${hostname}:8080`;
}

export function setAccessToken(token) {
  accessToken = token;
}

export function clearAccessToken() {
  accessToken = null;
}

export async function apiRequest(path, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const headers = new Headers(options.headers || {});
  const request = { ...options, headers, signal: controller.signal, credentials: 'omit' };

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  if (request.body && !(request.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  try {
    const response = await fetch(`${getApiBaseUrl()}${path}`, request);
    const bodyText = await response.text();
    let body = null;
    if (bodyText) {
      try {
        body = JSON.parse(bodyText);
      } catch {
        body = null;
      }
    }

    if (!response.ok) {
      const message = body?.detail || body?.message || body?.title || body?.error
        || `Le serveur a répondu avec le statut ${response.status}.`;
      throw new ApiError(message, response.status);
    }
    if (body === null && response.status !== 204) {
      throw new ApiError('La réponse du serveur est illisible.', response.status);
    }
    return body;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error?.name === 'AbortError') {
      throw new ApiError('Le serveur met trop de temps à répondre. Réessayez.');
    }
    throw new ApiError(
      'Impossible de joindre le serveur. Vérifiez son démarrage et l’adresse API configurée.'
    );
  } finally {
    clearTimeout(timeoutId);
  }
}

export async function apiFileRequest(path) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const headers = new Headers();
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  try {
    const response = await fetch(`${getApiBaseUrl()}${path}`, {
      headers,
      signal: controller.signal,
      credentials: 'omit',
    });
    if (!response.ok) {
      const bodyText = await response.text();
      let body = null;
      try {
        body = bodyText ? JSON.parse(bodyText) : null;
      } catch {
        body = null;
      }
      throw new ApiError(
        body?.detail || body?.message || body?.title || body?.error
          || `Le serveur a répondu avec le statut ${response.status}.`,
        response.status
      );
    }
    return await response.blob();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error?.name === 'AbortError') {
      throw new ApiError('Le téléchargement prend trop de temps. Réessayez.');
    }
    throw new ApiError('Impossible de télécharger le fichier. Vérifiez votre connexion.');
  } finally {
    clearTimeout(timeoutId);
  }
}

export async function downloadProtectedFile(path, fileName) {
  const blob = await apiFileRequest(path);
  if (Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android') {
    const dataUrl = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = () => reject(new ApiError('Impossible de préparer le justificatif pour Android.'));
      reader.readAsDataURL(blob);
    });
    const base64 = String(dataUrl).split(',', 2)[1];
    if (!base64) {
      throw new ApiError('Le justificatif téléchargé est vide ou invalide.');
    }
    const result = globalThis.UniAbsenceFile?.openFile(
      fileName,
      blob.type || 'application/octet-stream',
      base64
    );
    if (result !== 'opened') {
      throw new ApiError(result || 'Le lecteur Android des justificatifs est indisponible.');
    }
    return;
  }

  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = fileName;
  link.hidden = true;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

export async function login(email, password) {
  clearAccessToken();
  const response = await apiRequest('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });

  const roles = new Set(['STUDENT', 'TEACHER', 'ADMIN']);
  if (
    typeof response.token !== 'string'
    || !response.token
    || typeof response.tokenType !== 'string'
    || response.tokenType?.toLowerCase() !== 'bearer'
    || !roles.has(response.role)
    || response.user?.role !== response.role
    || !Number.isInteger(response.user?.id)
    || typeof response.user?.email !== 'string'
  ) {
    throw new ApiError('La réponse d’authentification du serveur est invalide.');
  }

  setAccessToken(response.token);
  return response;
}
