export interface User {
  id: string;
  username: string;
  displayName: string;
  role?: 'ADMIN' | 'USER' | 'SYSTEM';
  avatarUrl?: string;
  lastActiveAt?: string;
  createdAt?: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
  displayName: string;
  role: string;
}

export interface Room {
  id: string;
  name: string;
  type: 'GROUP' | 'DIRECT';
  isProtected?: boolean;
  isPrivate?: boolean;
  createdBy?: string;
  createdAt: string;
}

export interface Message {
  id: number;
  senderId: string;
  senderName: string;
  content: string;
  messageType: 'TEXT' | 'IMAGE' | 'FILE' | 'AUDIO';
  mediaUrl?: string;
  createdAt: string;
  replyToId?: number;
  replyToSenderName?: string;
  replyToContent?: string;
  deleted?: boolean;
}

export interface ChatMessagePayload {
  senderId: string;
  senderName: string;
  content: string | null;
  messageType?: string;
  mediaUrl?: string | null;
  type: 'CHAT' | 'TYPING' | 'IDLE' | 'DELETE';
  timestamp?: string;
  replyToId?: number;
  messageId?: number;
  deleted?: boolean;
}

export interface Friend {
  id: number;
  friendId: string;
  friendUsername: string;
  friendDisplayName: string;
  friendAvatarUrl?: string;
  status: 'PENDING' | 'ACCEPTED';
  createdAt: string;
}

export interface RoomMember {
  userId: string;
  username: string;
  displayName: string;
  avatarUrl?: string;
  role: 'OWNER' | 'MEMBER';
  joinedAt: string;
}

export interface UpdateRoomRequest {
  name?: string;
  password?: string;
}

export interface AdminDashboardStats {
  dailyMessages: number;
  weeklyMessages: number;
  monthlyMessages: number;
  activeUsersCount: number;
  onlineUsersCount: number;
  totalRoomsCount: number;
  dailyTokensUsed: number;
  weeklyTokensUsed: number;
  monthlyTokensUsed: number;
}

export interface UpdateProfileRequest {
  displayName?: string;
  avatarUrl?: string;
  currentPassword?: string;
  newPassword?: string;
}

export interface AppError {
  code: 401 | 403 | 404 | 500;
  title: string;
  message: string;
}
