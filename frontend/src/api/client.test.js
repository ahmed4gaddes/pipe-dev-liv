import MockAdapter from 'axios-mock-adapter';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../auth/keycloak', () => ({
  default: {
    token: 'fake-token',
    updateToken: vi.fn().mockResolvedValue(true),
  },
}));

// Chargé après le mock ci-dessus (client.js importe keycloak au niveau module).
const { default: client } = await import('./client');

describe('api client interceptors', () => {
  let mock;

  beforeEach(() => {
    mock = new MockAdapter(client);
  });

  afterEach(() => {
    mock.reset();
  });

  it('unwraps a successful ApiResponse envelope to its .data', async () => {
    mock.onGet('/api/tickets/1').reply(200, { success: true, message: 'ok', data: { id: 1, title: 'T' } });

    const result = await client.get('/api/tickets/1');

    expect(result).toEqual({ id: 1, title: 'T' });
  });

  it('rejects with the envelope message when success is false', async () => {
    mock.onGet('/api/tickets/2').reply(200, { success: false, message: 'Ticket introuvable' });

    await expect(client.get('/api/tickets/2')).rejects.toThrow('Ticket introuvable');
  });

  it('rejects with a usable Error on an HTTP failure', async () => {
    mock.onGet('/api/tickets/3').reply(500, { message: 'Erreur serveur' });

    await expect(client.get('/api/tickets/3')).rejects.toThrow('Erreur serveur');
  });

  it('attaches the Authorization header from the current keycloak token', async () => {
    let receivedAuth;
    mock.onGet('/api/tickets/4').reply((config) => {
      receivedAuth = config.headers.Authorization;
      return [200, { success: true, data: {} }];
    });

    await client.get('/api/tickets/4');

    expect(receivedAuth).toBe('Bearer fake-token');
  });
});
