import axios from 'axios';
import type {
  AuthResponse,
  Room,
  Message,
  User,
  Friend,
  RoomMember,
  AdminDashboardStats,
  UpdateProfileRequest,
  UpdateRoomRequest,
  AppNotification,
  NotificationMode,
  NotificationSettingsResponse,
  PresignedUploadRequest,
  PresignedUploadResponse,
} from '../types';

const API_BASE_URL = '/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

let onErrorHandler: ((error: { code: 401 | 403 | 404 | 500; title: string; message: string }) => void) | null = null;

export const setGlobalErrorHandler = (handler: typeof onErrorHandler) => {
  onErrorHandler = handler;
};

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const status = error.response.status;

      if (status === 401) {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        if (onErrorHandler) {
          onErrorHandler({
            code: 401,
            title: 'Session Expired',
            message: 'Your session has expired. Please sign in again.',
          });
        }
      } else if (status >= 500) {
        if (onErrorHandler) {
          onErrorHandler({
            code: 500,
            title: 'Server Error',
            message: 'An unexpected internal server error occurred. Please try again later.',
          });
        }
      }
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  login: async (username: string, password: string): Promise<AuthResponse> => {
    const res = await apiClient.post<AuthResponse>('/auth/login', { username, password });
    return res.data;
  },

  register: async (username: string, password: string, displayName: string): Promise<AuthResponse> => {
    const res = await apiClient.post<AuthResponse>('/auth/register', { username, password, displayName });
    return res.data;
  },
};

export const userApi = {
  getProfile: async (): Promise<User> => {
    const res = await apiClient.get<User>('/users/me');
    return res.data;
  },

  updateProfile: async (data: UpdateProfileRequest): Promise<User> => {
    const res = await apiClient.put<User>('/users/me', data);
    return res.data;
  },

  uploadAvatar: async (file: File): Promise<User> => {
    const formData = new FormData();
    formData.append('file', file);
    const res = await apiClient.post<User>('/users/me/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data;
  },

  deleteAccount: async (): Promise<void> => {
    await apiClient.delete('/users/me');
  },

  searchUsers: async (query: string): Promise<User[]> => {
    const res = await apiClient.get<User[]>(`/users/search?query=${encodeURIComponent(query)}`);
    return res.data;
  },

  getPublicProfile: async (userId: string): Promise<User> => {
    const res = await apiClient.get<User>(`/users/${userId}/profile`);
    return res.data;
  },
};

export const friendApi = {
  sendRequest: async (friendId: string): Promise<void> => {
    await apiClient.post('/friends/request', { friendId });
  },

  acceptRequest: async (friendshipId: number): Promise<void> => {
    await apiClient.post(`/friends/accept/${friendshipId}`);
  },

  declineRequest: async (friendshipId: number): Promise<void> => {
    await apiClient.post(`/friends/decline/${friendshipId}`);
  },

  getRequests: async (): Promise<Friend[]> => {
    const res = await apiClient.get<Friend[]>('/friends/requests');
    return res.data;
  },

  getFriends: async (): Promise<Friend[]> => {
    const res = await apiClient.get<Friend[]>('/friends');
    return res.data;
  },

  getOrCreateDM: async (friendId: string): Promise<Room> => {
    const res = await apiClient.post<Room>(`/friends/dm?friendId=${encodeURIComponent(friendId)}`);
    return res.data;
  },

  unfriend: async (friendId: string): Promise<void> => {
    await apiClient.delete(`/friends/${encodeURIComponent(friendId)}`);
  },
};

export const roomApi = {
  getRooms: async (): Promise<Room[]> => {
    const res = await apiClient.get<Room[]>('/rooms');
    return res.data;
  },

  getJoinedRooms: async (): Promise<Room[]> => {
    const res = await apiClient.get<Room[]>('/rooms/joined');
    return res.data;
  },

  searchRooms: async (query: string): Promise<Room[]> => {
    const res = await apiClient.get<Room[]>(`/rooms/search?query=${encodeURIComponent(query)}`);
    return res.data;
  },

  createRoom: async (name: string, password?: string, isPrivate?: boolean): Promise<Room> => {
    const res = await apiClient.post<Room>('/rooms', { name, password, isPrivate });
    return res.data;
  },

  getRoom: async (roomId: string): Promise<Room> => {
    const res = await apiClient.get<Room>(`/rooms/${roomId}`);
    return res.data;
  },

  getInviteLink: async (roomId: string): Promise<{ url: string }> => {
    const res = await apiClient.get<{ url: string }>(`/rooms/${roomId}/invite`);
    return res.data;
  },

  getMessages: async (roomId: string): Promise<Message[]> => {
    const res = await apiClient.get<Message[]>(`/rooms/${roomId}/messages`);
    return res.data;
  },

  getMembers: async (roomId: string): Promise<RoomMember[]> => {
    const res = await apiClient.get<RoomMember[]>(`/rooms/${roomId}/members`);
    return res.data;
  },

  updateRoom: async (roomId: string, data: UpdateRoomRequest): Promise<void> => {
    await apiClient.patch(`/rooms/${roomId}`, data);
  },

  joinRoom: async (roomId: string, password?: string): Promise<void> => {
    await apiClient.post(`/rooms/${roomId}/join`, { password });
  },

  deleteRoom: async (roomId: string): Promise<void> => {
    await apiClient.delete(`/rooms/${roomId}`);
  },

  kickMember: async (roomId: string, targetUserId: string): Promise<void> => {
    await apiClient.delete(`/rooms/${roomId}/members/${targetUserId}`);
  },

  deleteMessage: async (roomId: string, messageId: number): Promise<void> => {
    await apiClient.delete(`/rooms/${roomId}/messages/${messageId}`);
  },

  getNotificationSettings: async (roomId: string): Promise<NotificationSettingsResponse> => {
    const res = await apiClient.get<NotificationSettingsResponse>(`/rooms/${roomId}/notification-settings`);
    return res.data;
  },

  updateNotificationSettings: async (roomId: string, mode: NotificationMode): Promise<void> => {
    await apiClient.put(`/rooms/${roomId}/notification-settings`, { mode });
  },

  markRoomRead: async (roomId: string): Promise<void> => {
    await apiClient.post(`/rooms/${roomId}/read`);
  },
};

export const mediaApi = {
  requestPresignedUpload: async (data: PresignedUploadRequest): Promise<PresignedUploadResponse> => {
    const res = await apiClient.post<PresignedUploadResponse>('/uploads/presign', data);
    return res.data;
  },

  uploadToR2: async (uploadUrl: string, file: File, onProgress?: (percent: number) => void): Promise<void> => {
    await axios.put(uploadUrl, file, {
      headers: { 'Content-Type': file.type || 'application/octet-stream' },
      onUploadProgress: (e: any) => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded / e.total) * 100));
        }
      },
    });
  },
};

export const notificationApi = {
  getNotifications: async (limit = 50): Promise<AppNotification[]> => {
    const res = await apiClient.get<AppNotification[]>(`/notifications?limit=${limit}`);
    return res.data;
  },

  getUnreadCount: async (): Promise<number> => {
    const res = await apiClient.get<{ count: number }>('/notifications/unread-count');
    return res.data.count;
  },

  markRead: async (id: number): Promise<void> => {
    await apiClient.post(`/notifications/${id}/read`);
  },

  markAllRead: async (): Promise<void> => {
    await apiClient.post('/notifications/read-all');
  },
};

export const adminApi = {
  getDashboardStats: async (): Promise<AdminDashboardStats> => {
    const res = await apiClient.get<AdminDashboardStats>('/admin/dashboard/stats');
    return res.data;
  },

  getUsers: async (): Promise<User[]> => {
    const res = await apiClient.get<User[]>('/admin/users');
    return res.data;
  },

  deleteUser: async (userId: string, hard = false): Promise<void> => {
    await apiClient.delete(`/admin/users/${userId}?hard=${hard}`);
  },

  getRooms: async (): Promise<Room[]> => {
    const res = await apiClient.get<Room[]>('/admin/rooms');
    return res.data;
  },

  deleteRoom: async (roomId: string, hard = false): Promise<void> => {
    await apiClient.delete(`/admin/rooms/${roomId}?hard=${hard}`);
  },

  getMessages: async (): Promise<Message[]> => {
    const res = await apiClient.get<Message[]>('/admin/messages');
    return res.data;
  },

  deleteMessage: async (messageId: number, hard = false): Promise<void> => {
    await apiClient.delete(`/admin/messages/${messageId}?hard=${hard}`);
  },
};
