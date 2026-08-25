import React, { useState, useEffect } from 'react';
import type { Friend, User, Room } from '../../types';
import { friendApi, userApi } from '../../services/api';
import { X, UserPlus, MessageSquare, Search, Users, Check, Loader2, UserMinus } from 'lucide-react';
import { Avatar } from '../ui/Avatar';
import { UserProfileCard } from '../profile/UserProfileCard';
import { FriendContextMenu } from './FriendContextMenu';

interface FriendsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectRoom: (roomId: string) => void;
  onFriendListChanged?: () => void;
}

export const FriendsModal: React.FC<FriendsModalProps> = ({ isOpen, onClose, onSelectRoom, onFriendListChanged }) => {
  const [activeTab, setActiveTab] = useState<'friends' | 'requests' | 'add'>('friends');
  const [friends, setFriends] = useState<Friend[]>([]);
  const [pendingRequests, setPendingRequests] = useState<Friend[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<User[]>([]);
  const [sentRequests, setSentRequests] = useState<Set<string>>(new Set());
  const [sendError, setSendError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isRequestsLoading, setIsRequestsLoading] = useState(false);
  const [confirmUnfriendId, setConfirmUnfriendId] = useState<string | null>(null);
  const [viewingUserId, setViewingUserId] = useState<string | null>(null);
  const [friendMenu, setFriendMenu] = useState<{ friend: Friend; x: number; y: number } | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    const handleEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [isOpen, onClose]);

  useEffect(() => {
    if (isOpen && activeTab === 'friends') {
      loadFriends();
    }
  }, [isOpen, activeTab]);

  useEffect(() => {
    if (isOpen && activeTab === 'requests') {
      loadRequests();
    }
  }, [isOpen, activeTab]);

  const loadFriends = async () => {
    setIsLoading(true);
    try {
      const data = await friendApi.getFriends();
      setFriends(data);
    } catch (err) {
      console.error('Failed to load friends:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const loadRequests = async () => {
    setIsRequestsLoading(true);
    try {
      const data = await friendApi.getRequests();
      setPendingRequests(data);
    } catch (err) {
      console.error('Failed to load friend requests:', err);
    } finally {
      setIsRequestsLoading(false);
    }
  };

  const handleSearchUsers = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    setSendError(null);
    setIsLoading(true);
    try {
      const results = await userApi.searchUsers(searchQuery.trim());
      setSearchResults(results);
    } catch (err) {
      console.error('User search failed:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSendRequest = async (friendId: string) => {
    try {
      await friendApi.sendRequest(friendId);
      setSendError(null);
      setSentRequests((prev) => new Set(prev).add(friendId));
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || '';
      if (msg.toLowerCase().includes('already sent') || msg.toLowerCase().includes('already exists')) {
        setSendError(null);
        setSentRequests((prev) => new Set(prev).add(friendId));
      } else {
        setSendError(msg || 'Failed to send friend request');
      }
    }
  };

  const handleAcceptRequest = async (friendshipId: number) => {
    try {
      await friendApi.acceptRequest(friendshipId);
      setPendingRequests((prev) => prev.filter((r) => r.id !== friendshipId));
      await loadFriends();
    } catch (err) {
      console.error('Failed to accept friend request:', err);
    }
  };

  const handleDeclineRequest = async (friendshipId: number) => {
    try {
      await friendApi.declineRequest(friendshipId);
      setPendingRequests((prev) => prev.filter((r) => r.id !== friendshipId));
    } catch (err) {
      console.error('Failed to decline friend request:', err);
    }
  };

  const handleStartDM = async (friendId: string) => {
    try {
      const room: Room = await friendApi.getOrCreateDM(friendId);
      onSelectRoom(room.id);
      onClose();
    } catch (err) {
      console.error('Failed to start DM:', err);
    }
  };

  const handleUnfriend = async (friendId: string) => {
    try {
      await friendApi.unfriend(friendId);
      setFriends((prev) => prev.filter((f) => f.friendId !== friendId));
      setConfirmUnfriendId(null);
      onFriendListChanged?.();
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Failed to unfriend';
      setSendError(msg);
    }
  };

  const handleFriendContextMenu = (e: React.MouseEvent, friend: Friend) => {
    e.preventDefault();
    e.stopPropagation();
    setFriendMenu({
      friend,
      x: Math.min(e.clientX, window.innerWidth - 220),
      y: Math.min(e.clientY, window.innerHeight - 280),
    });
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm" onClick={onClose}>
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative max-h-[85vh] flex flex-col" onClick={(e) => e.stopPropagation()}>
        <button
          onClick={onClose}
          className="absolute top-5 right-5 text-slate-400 hover:text-white p-1 rounded-xl hover:bg-slate-800 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        <h2 className="text-xl font-bold text-white tracking-tight mb-4 flex items-center gap-2">
          <Users className="w-5 h-5 text-blue-400" />
          Friends & Direct Messages
        </h2>

        {/* Tab Switcher */}
        <div className="flex p-1 bg-slate-950/60 rounded-xl mb-4 border border-slate-800 gap-1">
          <button
            onClick={() => setActiveTab('friends')}
            className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'friends' ? 'bg-slate-800 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            My Friends ({friends.length})
          </button>
          <button
            onClick={() => setActiveTab('requests')}
            className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all relative ${
              activeTab === 'requests' ? 'bg-slate-800 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Requests
            {pendingRequests.length > 0 && (
              <span className="ml-1.5 inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-blue-500 text-white text-[10px] font-bold">
                {pendingRequests.length}
              </span>
            )}
          </button>
          <button
            onClick={() => setActiveTab('add')}
            className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'add' ? 'bg-slate-800 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Add Friends
          </button>
        </div>

        {activeTab === 'friends' && (
          <div className="flex-1 overflow-y-auto space-y-2 pr-1">
            {isLoading ? (
              <div className="py-12 text-center text-slate-500">
                <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2 text-blue-500" />
                <span>Loading friends...</span>
              </div>
            ) : friends.length === 0 ? (
              <div className="py-12 text-center text-slate-500 text-xs">
                No friends added yet. Switch to "Add Friends" to find users!
              </div>
            ) : (
              friends.map((f) => {
                const isConfirming = confirmUnfriendId === f.friendId;
                return (
                  <div
                    key={f.id}
                    onContextMenu={(e) => handleFriendContextMenu(e, f)}
                    className="flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800/80 hover:border-slate-700 transition-all"
                  >
                    <div className="flex items-center gap-3 cursor-pointer" onClick={() => setViewingUserId(f.friendId)}>
                      <Avatar name={f.friendDisplayName} src={f.friendAvatarUrl} size="md" />
                      <div>
                        <h4 className="text-sm font-bold text-white leading-tight hover:underline">{f.friendDisplayName}</h4>
                        <span className="text-xs text-slate-400 font-mono">@{f.friendUsername}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleStartDM(f.friendId)}
                        className="px-3 py-1.5 bg-blue-600/10 hover:bg-blue-600/20 border border-blue-500/20 text-blue-400 text-xs font-semibold rounded-xl flex items-center gap-1.5 transition-colors"
                      >
                        <MessageSquare className="w-3.5 h-3.5" />
                        <span>Message</span>
                      </button>
                      {isConfirming ? (
                        <div className="flex items-center gap-1.5">
                          <button
                            onClick={() => handleUnfriend(f.friendId)}
                            className="px-3 py-1.5 bg-red-600 hover:bg-red-500 text-white text-xs font-semibold rounded-xl transition-colors"
                          >
                            Confirm
                          </button>
                          <button
                            onClick={() => setConfirmUnfriendId(null)}
                            className="px-2 py-1.5 text-slate-400 hover:text-white text-xs font-medium rounded-xl hover:bg-slate-800 transition-colors"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <button
                          onClick={() => setConfirmUnfriendId(f.friendId)}
                          title="Unfriend (deletes the whole chat)"
                          className="px-2.5 py-1.5 bg-red-600/10 hover:bg-red-600/20 border border-red-500/20 text-red-400 text-xs font-semibold rounded-xl flex items-center gap-1.5 transition-colors"
                        >
                          <UserMinus className="w-3.5 h-3.5" />
                          <span>Unfriend</span>
                        </button>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}

        {activeTab === 'requests' && (
          <div className="flex-1 overflow-y-auto space-y-2 pr-1">
            {isRequestsLoading ? (
              <div className="py-12 text-center text-slate-500">
                <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2 text-blue-500" />
                <span>Loading requests...</span>
              </div>
            ) : pendingRequests.length === 0 ? (
              <div className="py-12 text-center text-slate-500 text-xs">
                No pending friend requests.
              </div>
            ) : (
              pendingRequests.map((f) => (
                <div
                  key={f.id}
                  className="flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800/80"
                >
                  <div className="flex items-center gap-3">
                    <Avatar name={f.friendDisplayName} src={f.friendAvatarUrl} size="md" />
                    <div>
                      <h4 className="text-sm font-bold text-white leading-tight">{f.friendDisplayName}</h4>
                      <span className="text-xs text-slate-400 font-mono">@{f.friendUsername}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleAcceptRequest(f.id)}
                      className="px-3 py-1.5 bg-emerald-600/10 hover:bg-emerald-600/20 border border-emerald-500/20 text-emerald-400 text-xs font-semibold rounded-xl flex items-center gap-1.5 transition-colors"
                    >
                      <Check className="w-3.5 h-3.5" />
                      <span>Accept</span>
                    </button>
                    <button
                      onClick={() => handleDeclineRequest(f.id)}
                      className="px-3 py-1.5 bg-red-600/10 hover:bg-red-600/20 border border-red-500/20 text-red-400 text-xs font-semibold rounded-xl flex items-center gap-1.5 transition-colors"
                    >
                      <X className="w-3.5 h-3.5" />
                      <span>Decline</span>
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {activeTab === 'add' && (
          <div className="flex-1 flex flex-col space-y-4">
            <form onSubmit={handleSearchUsers} className="flex gap-2">
              <div className="relative flex-1">
                <Search className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search user by name or username..."
                  className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
                />
              </div>
              <button
                type="submit"
                className="px-4 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl shadow-lg shadow-blue-600/20"
              >
                Search
              </button>
            </form>

            {sendError && (
              <div className="px-3 py-2 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs font-medium">
                {sendError}
              </div>
            )}

            <div className="flex-1 overflow-y-auto space-y-2 pr-1">
              {isLoading ? (
                <div className="py-8 text-center text-slate-500">
                  <Loader2 className="w-5 h-5 animate-spin mx-auto mb-2 text-blue-500" />
                </div>
              ) : searchResults.length === 0 ? (
                <div className="py-8 text-center text-slate-500 text-xs">
                  {searchQuery ? 'No users found matching query' : 'Enter a search term above'}
                </div>
              ) : (
                searchResults.map((u) => {
                  const isSent = sentRequests.has(u.id);
                  return (
                    <div
                      key={u.id}
                      className="flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800/80"
                    >
                      <div className="flex items-center gap-3">
                        <Avatar name={u.displayName} src={u.avatarUrl} size="md" />
                        <div>
                          <h4 className="text-sm font-bold text-white leading-tight">{u.displayName}</h4>
                          <span className="text-xs text-slate-400 font-mono">@{u.username}</span>
                        </div>
                      </div>

                      {isSent ? (
                        <span className="text-xs text-emerald-400 flex items-center gap-1 px-3 py-1.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                          <Check className="w-3.5 h-3.5" /> Request Sent
                        </span>
                      ) : (
                        <button
                          onClick={() => handleSendRequest(u.id)}
                          className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl flex items-center gap-1.5 shadow-md shadow-blue-600/20 transition-all"
                        >
                          <UserPlus className="w-3.5 h-3.5" />
                          <span>Add Friend</span>
                        </button>
                      )}
                    </div>
                  );
                })
              )}
            </div>
          </div>
        )}
      </div>
      {viewingUserId && (
        <UserProfileCard userId={viewingUserId} onClose={() => setViewingUserId(null)} />
      )}
      {friendMenu && (
        <FriendContextMenu
          friend={friendMenu.friend}
          x={friendMenu.x}
          y={friendMenu.y}
          onClose={() => setFriendMenu(null)}
          onViewProfile={(userId) => { setViewingUserId(userId); setFriendMenu(null); }}
          onUnfriend={(friendId) => { handleUnfriend(friendId); setFriendMenu(null); }}
          showMessageOption
          onMessage={(friendId) => { handleStartDM(friendId); setFriendMenu(null); }}
        />
      )}
    </div>
  );
};