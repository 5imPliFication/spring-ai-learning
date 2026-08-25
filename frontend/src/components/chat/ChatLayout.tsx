import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useParams, useNavigate } from 'react-router';
import { useAuth } from '../../context/AuthContext';
import { useNotifications } from '../../context/NotificationContext';
import type { Room, Message, ChatMessagePayload, User, AppError, MessageMedia, RoomMember, NotificationMode, Friend } from '../../types';
import { roomApi, friendApi, setGlobalErrorHandler } from '../../services/api';
import { wsService } from '../../services/websocket';
import { Sidebar } from './Sidebar';
import { ChatHeader } from './ChatHeader';
import { MessageFeed } from './MessageFeed';
import { MessageInput } from './MessageInput';
import { CreateRoomModal } from './CreateRoomModal';
import { DiscoverRoomsModal } from './DiscoverRoomsModal';
import { JoinRoomModal } from './JoinRoomModal';
import { RoomSettingsModal } from './RoomSettingsModal';
import { ProfileModal } from '../profile/ProfileModal';
import { UserProfileCard } from '../profile/UserProfileCard';
import { FriendsModal } from '../friends/FriendsModal';
import { AdminDashboard } from '../admin/AdminDashboard';
import { ErrorPage } from '../common/ErrorPage';
import { MessageSquare, Sparkles } from 'lucide-react';

const mediaLabel = (m: Message | undefined): string | undefined => {
  if (!m?.mediaUrl) return undefined;
  switch (m.messageType) {
    case 'IMAGE':
      return '[Image]';
    case 'AUDIO':
      return '[Audio]';
    default:
      return '[File]';
  }
};

export const ChatLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const { setActiveRoomId: setNotifActiveRoomId, roomUnreadDeltas, clearRoomDelta } = useNotifications();
  const { roomId } = useParams();
  const navigate = useNavigate();
  const [currentUser, setCurrentUser] = useState<User | null>(user);
  const [joinedRooms, setJoinedRooms] = useState<Room[]>([]);
  const [activeRoomId, setActiveRoomId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [roomMembers, setRoomMembers] = useState<RoomMember[]>([]);
  const [mentionedMessageIds, setMentionedMessageIds] = useState<Set<number>>(new Set());
  const [isAiTyping, setIsAiTyping] = useState(false);
  const [replyingTo, setReplyingTo] = useState<Message | null>(null);
  const joiningRoomRef = useRef<string | null>(null);
  const subscribedRoomRef = useRef<string | null>(null);

  // Modals & Views State
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isDiscoverModalOpen, setIsDiscoverModalOpen] = useState(false);
  const [isProfileModalOpen, setIsProfileModalOpen] = useState(false);
  const [isFriendsModalOpen, setIsFriendsModalOpen] = useState(false);
  const [isSettingsModalOpen, setIsSettingsModalOpen] = useState(false);
  const [pendingJoinRoom, setPendingJoinRoom] = useState<Room | null>(null);
  const [isAdminViewOpen, setIsAdminViewOpen] = useState(false);
  const [friendsRefreshKey, setFriendsRefreshKey] = useState(0);
  const [friendsList, setFriendsList] = useState<Friend[]>([]);
  const [viewingUserId, setViewingUserId] = useState<string | null>(null);

  // Global Error State
  const [appError, setAppError] = useState<AppError | null>(null);
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false);

  useEffect(() => {
    setCurrentUser(user);
  }, [user]);

  useEffect(() => {
    setGlobalErrorHandler((err) => {
      setAppError(err);
    });
  }, []);

  const activeRoom = joinedRooms.find((r) => r.id === activeRoomId) || null;

  useEffect(() => {
    setNotifActiveRoomId(activeRoomId);
  }, [activeRoomId, setNotifActiveRoomId]);

  const fetchJoinedRooms = useCallback(async () => {
    const data = await roomApi.getJoinedRooms();
    setJoinedRooms(data);
    return data;
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await fetchJoinedRooms();
        if (cancelled) return;
        if (!roomId && data.length > 0) {
          navigate(`/rooms/${data[0].id}`, { replace: true });
        }
      } catch (err) {
        console.error('Failed to load joined rooms:', err);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [fetchJoinedRooms, roomId, navigate]);

  useEffect(() => {
    friendApi.getFriends().then(setFriendsList).catch(() => {});
  }, [friendsRefreshKey]);

  const handleFriendUnfriended = useCallback((friendId: string) => {
    setFriendsList((prev) => prev.filter((f) => f.friendId !== friendId));
  }, []);

  const handleIncomingWebSocketMessage = useCallback((payload: ChatMessagePayload) => {
    if (payload.type === 'TYPING') {
      setIsAiTyping(true);
      return;
    }

    if (payload.type === 'IDLE') {
      setIsAiTyping(false);
      return;
    }

    if (payload.type === 'DELETE') {
      if (payload.messageId != null) {
        setMessages((prev) =>
          prev.map((m) => (m.id === payload.messageId ? { ...m, deleted: true, content: '' } : m))
        );
      }
      return;
    }

    if (payload.type === 'CHAT') {
      if (payload.senderId === 'ai-bot') {
        setIsAiTyping(false);
      }
      const mentionsMe = !!currentUser?.id
        && !!payload.mentionedUserIds?.includes(currentUser.id);
      if (mentionsMe && payload.messageId != null) {
        setMentionedMessageIds((prev) => new Set(prev).add(payload.messageId as number));
      }
      setMessages((prev) => {
        const replyMsg = payload.replyToId != null
          ? prev.find((m) => m.id === payload.replyToId)
          : undefined;
        const newMsg: Message = {
          id: payload.messageId ?? Date.now(),
          senderId: payload.senderId,
          senderName: payload.senderName,
          content: payload.content || '',
          messageType: (payload.messageType as any) || 'TEXT',
          mediaUrl: payload.mediaUrl || undefined,
          createdAt: payload.timestamp || new Date().toISOString(),
          replyToId: payload.replyToId ?? undefined,
          replyToSenderName: replyMsg?.senderName,
          replyToContent: replyMsg?.deleted ? undefined : (replyMsg?.content || mediaLabel(replyMsg)),
          deleted: false,
        };
        return [...prev, newMsg];
      });
    }
  }, [currentUser]);

  const executeJoinRoom = useCallback(async (roomId: string, password?: string) => {
    setActiveRoomId(roomId);
    setIsMobileSidebarOpen(false);
    setIsAiTyping(false);

    try {
      await roomApi.joinRoom(roomId, password);
      await fetchJoinedRooms();
      const history = await roomApi.getMessages(roomId);
      setMessages(history);
      setReplyingTo(null);
      setMentionedMessageIds(new Set());
      clearRoomDelta(roomId);
      roomApi.markRoomRead(roomId).catch((err) =>
        console.error('Failed to mark room read:', err)
      );

      try {
        const members = await roomApi.getMembers(roomId);
        setRoomMembers(members);
      } catch (err) {
        console.error('Failed to load room members:', err);
        setRoomMembers([]);
      }

      if (subscribedRoomRef.current && subscribedRoomRef.current !== roomId) {
        wsService.unsubscribeRoom(subscribedRoomRef.current);
      }
      wsService.subscribeToRoom(roomId, handleIncomingWebSocketMessage);
      subscribedRoomRef.current = roomId;
    } catch (err: any) {
      console.error('Error joining room or fetching messages:', err);
      throw err;
    }
  }, [fetchJoinedRooms, handleIncomingWebSocketMessage, clearRoomDelta]);

  const handleSelectRoom = (roomId: string) => {
    navigate(`/rooms/${roomId}`);
  };

  useEffect(() => {
    if (!roomId || joiningRoomRef.current === roomId) return;

    joiningRoomRef.current = roomId;
    executeJoinRoom(roomId)
      .catch((err: any) => {
        const msg = err.response?.data?.message || err.message || '';
        if (msg.toLowerCase().includes('password')) {
          (async () => {
            try {
              const all = await roomApi.getRooms();
              const target = all.find((r) => r.id === roomId);
              if (target) {
                setPendingJoinRoom(target);
              }
            } catch (e) {
              console.error('Failed to get room for password prompt:', e);
            }
          })();
        }
      })
      .finally(() => {
        if (joiningRoomRef.current === roomId) joiningRoomRef.current = null;
      });
  }, [roomId, executeJoinRoom]);

  useEffect(() => {
    return () => {
      if (subscribedRoomRef.current) {
        wsService.unsubscribeRoom(subscribedRoomRef.current);
        subscribedRoomRef.current = null;
      }
    };
  }, []);

  const handleCreateRoom = async (name: string, password?: string, isPrivate?: boolean) => {
    const newRoom = await roomApi.createRoom(name, password, isPrivate);
    await fetchJoinedRooms();
    navigate(`/rooms/${newRoom.id}`);
  };

  const handleSendMessage = (content: string, replyToId?: number, media?: MessageMedia) => {
    if (!activeRoomId || !currentUser) return;

    const payload: ChatMessagePayload = {
      senderId: currentUser.id,
      senderName: currentUser.displayName,
      content: media ? (content || null) : content,
      messageType: media?.messageType ?? 'TEXT',
      mediaUrl: media?.mediaUrl ?? null,
      type: 'CHAT',
      replyToId,
    };

    wsService.sendMessage(activeRoomId, payload);
    if (replyToId) setReplyingTo(null);
  };

  const handleDeleteMessage = async (messageId: number) => {
    if (!activeRoomId) return;
    try {
      await roomApi.deleteMessage(activeRoomId, messageId);
      setMessages((prev) =>
        prev.map((m) => (m.id === messageId ? { ...m, deleted: true, content: '' } : m))
      );
    } catch (err) {
      console.error('Failed to delete message:', err);
    }
  };

  const handleRoomDeleted = (deletedId: string) => {
    setJoinedRooms((prev) => prev.filter((r) => r.id !== deletedId));
    if (activeRoomId === deletedId) {
      setActiveRoomId(null);
      setMessages([]);
      navigate('/');
    }
  };

  const handleUpdateNotificationMode = async (targetRoomId: string, mode: NotificationMode) => {
    try {
      await roomApi.updateNotificationSettings(targetRoomId, mode);
      setJoinedRooms((prev) =>
        prev.map((r) =>
          r.id === targetRoomId
            ? { ...r, notificationMode: mode, unreadCount: mode === 'MUTED' ? 0 : r.unreadCount }
            : r
        )
      );
    } catch (err) {
      console.error('Failed to update notification settings:', err);
    }
  };

  const sidebarRooms = useMemo(
    () =>
      joinedRooms.map((room) => ({
        ...room,
        unreadCount:
          room.notificationMode === 'MUTED'
            ? 0
            : Math.max(0, (room.unreadCount ?? 0) + (roomUnreadDeltas[room.id] ?? 0)),
        notificationMode: (room.notificationMode ?? 'ALL') as NotificationMode,
      })),
    [joinedRooms, roomUnreadDeltas]
  );

  if (appError) {
    return <ErrorPage error={appError} onClearError={() => setAppError(null)} />;
  }

  if (isAdminViewOpen) {
    return <AdminDashboard onClose={() => setIsAdminViewOpen(false)} />;
  }

  if (!currentUser) return null;

  return (
    <div className="flex h-screen w-screen bg-slate-950 text-slate-100 overflow-hidden relative">
      {/* Mobile Sidebar Overlay */}
      {isMobileSidebarOpen && (
        <div
          onClick={() => setIsMobileSidebarOpen(false)}
          className="md:hidden fixed inset-0 z-40 bg-slate-950/80 backdrop-blur-sm"
        />
      )}

      {/* Sidebar */}
      <div
        className={`fixed md:relative z-40 h-full transition-transform duration-300 ${
          isMobileSidebarOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
        }`}
      >
        <Sidebar
          user={currentUser}
          rooms={sidebarRooms}
          activeRoomId={activeRoomId}
          onSelectRoom={handleSelectRoom}
          onOpenCreateRoom={() => setIsCreateModalOpen(true)}
          onOpenDiscoverRooms={() => setIsDiscoverModalOpen(true)}
          onOpenProfile={() => setIsProfileModalOpen(true)}
          onOpenFriends={() => setIsFriendsModalOpen(true)}
          onOpenAdmin={() => setIsAdminViewOpen(true)}
          onLogout={logout}
          onCloseMobile={() => setIsMobileSidebarOpen(false)}
          friendsRefreshKey={friendsRefreshKey}
          onUpdateNotificationMode={handleUpdateNotificationMode}
          onViewProfile={(userId) => setViewingUserId(userId)}
        />
      </div>

      {/* Main Chat Area */}
      <main className="flex-1 flex flex-col h-full min-w-0 bg-slate-950">
        {viewingUserId ? (
          <UserProfileCard userId={viewingUserId} onClose={() => setViewingUserId(null)} />
        ) : activeRoom ? (
          <>
            <ChatHeader
              room={activeRoom}
              isAiTyping={isAiTyping}
              onToggleSidebar={() => setIsMobileSidebarOpen(true)}
              onOpenSettings={() => setIsSettingsModalOpen(true)}
            />
            <MessageFeed
              messages={messages}
              currentUserId={currentUser.id}
              isAiTyping={isAiTyping}
              roomOwnerId={activeRoom.createdBy}
              currentUserRole={currentUser.role}
              members={roomMembers}
              mentionedMessageIds={mentionedMessageIds}
              onReply={setReplyingTo}
              onDelete={handleDeleteMessage}
              friends={friendsList}
              onViewProfile={(userId) => setViewingUserId(userId)}
              onUnfriend={handleFriendUnfriended}
            />
            <MessageInput
              onSendMessage={handleSendMessage}
              replyingTo={replyingTo}
              onCancelReply={() => setReplyingTo(null)}
              members={roomMembers}
            />
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center p-6 text-center text-slate-500">
            <div className="w-16 h-16 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center mb-4">
              <MessageSquare className="w-8 h-8 text-slate-400" />
            </div>
            <h3 className="text-lg font-bold text-white mb-1">Select or Discover a Chat Room</h3>
            <p className="text-sm text-slate-400 max-w-sm mb-6">
              Choose a room from your joined list, explore public rooms with Discover, or start a new room!
            </p>
            <div className="flex items-center gap-3">
              <button
                onClick={() => setIsDiscoverModalOpen(true)}
                className="px-5 py-2.5 bg-slate-800 hover:bg-slate-700 text-white text-sm font-medium rounded-xl border border-slate-700 transition-all"
              >
                Discover Rooms
              </button>
              <button
                onClick={() => setIsCreateModalOpen(true)}
                className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-xl shadow-lg shadow-blue-600/20 flex items-center gap-2 transition-all"
              >
                <Sparkles className="w-4 h-4" />
                <span>Create Room</span>
              </button>
            </div>
          </div>
        )}
      </main>

      {/* Modals */}
      <CreateRoomModal
        isOpen={isCreateModalOpen}
        onClose={() => setIsCreateModalOpen(false)}
        onCreate={handleCreateRoom}
      />

      <DiscoverRoomsModal
        isOpen={isDiscoverModalOpen}
        onClose={() => setIsDiscoverModalOpen(false)}
        onSelectRoom={handleSelectRoom}
      />

      <JoinRoomModal
        room={pendingJoinRoom}
        isOpen={!!pendingJoinRoom}
        onClose={() => setPendingJoinRoom(null)}
        onConfirmJoin={executeJoinRoom}
      />

      <RoomSettingsModal
        room={activeRoom}
        currentUser={currentUser}
        isOpen={isSettingsModalOpen}
        onClose={() => setIsSettingsModalOpen(false)}
        onRoomDeleted={handleRoomDeleted}
        onRoomUpdated={fetchJoinedRooms}
      />

      <ProfileModal
        user={currentUser}
        isOpen={isProfileModalOpen}
        onClose={() => setIsProfileModalOpen(false)}
        onProfileUpdated={(updated) => setCurrentUser(updated)}
        onLogout={logout}
      />

      <FriendsModal
        isOpen={isFriendsModalOpen}
        onClose={() => setIsFriendsModalOpen(false)}
        onSelectRoom={handleSelectRoom}
        onFriendListChanged={() => setFriendsRefreshKey((k) => k + 1)}
      />
    </div>
  );
};
