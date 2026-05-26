import client from './client';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  nickname?: string;
}

export interface UserInfo {
  userId: number;
  username: string;
  nickname: string;
  level: number;
  winCount: number;
  loseCount: number;
}

export const authApi = {
  login(data: LoginRequest) {
    return client.post<{ code: number; message: string; data: { token: string; user: UserInfo } }>('/api/auth/login', data);
  },

  register(data: RegisterRequest) {
    return client.post<{ code: number; message: string; data: null }>('/api/auth/register', data);
  },

  getUserInfo() {
    return client.get<{ code: number; message: string; data: UserInfo }>('/api/user/info');
  },
};