import { Client } from '@stomp/stompjs';

let stompClient: Client | null = null;

export function getStompClient(): Client {
  if (!stompClient) {
    stompClient = new Client({
      brokerURL: import.meta.env.VITE_WS_URL,
      connectHeaders: {
        get Authorization() {
          return localStorage.getItem('accessToken') ?? '';
        },
      },
      reconnectDelay: 3000,
    });
  }
  return stompClient;
}

export function connectStomp(): Promise<void> {
  return new Promise((resolve, reject) => {
    const client = getStompClient();
    if (client.connected) {
      resolve();
      return;
    }
    client.onConnect = () => resolve();
    client.onStompError = (frame) => reject(new Error(frame.headers.message));
    client.activate();
  });
}

export function disconnectStomp(): void {
  stompClient?.deactivate();
  stompClient = null;
}
