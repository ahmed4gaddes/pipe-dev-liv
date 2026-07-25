import axios from 'axios';
import keycloak from '../auth/keycloak';

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

// Le token peut expirer entre deux appels ; on le rafraîchit proactivement (marge 30s)
// avant chaque requête plutôt que de laisser un 401 arriver puis réessayer.
client.interceptors.request.use(async (config) => {
  if (keycloak.token) {
    try {
      await keycloak.updateToken(30);
    } catch {
      keycloak.login();
    }
    config.headers.Authorization = `Bearer ${keycloak.token}`;
  }
  return config;
});

// Déballe ApiResponse<T> ({ success, message, data }) — voir common-lib.ApiResponse —
// et transforme un success:false ou un statut non-2xx en erreur exploitable par React Query.
client.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body === 'object' && 'success' in body) {
      if (!body.success) {
        return Promise.reject(new Error(body.message || 'Erreur inconnue'));
      }
      return body.data;
    }
    return body;
  },
  (error) => {
    const message = error.response?.data?.message || error.message || 'Erreur réseau';
    return Promise.reject(new Error(message));
  }
);

export default client;
