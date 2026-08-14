import React, { useState, useEffect } from 'react';
import type { Friend, User, Room } from '../../types';
import { friendApi, userApi } from '../../services/api';
import { X, UserPlus, MessageSquare, Search, Users, Check, Loader2 } from 'lucide-react';
import { Avatar } from '../ui/Avatar';

interface FriendsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectRoom: (roomId: string) => void;
}

export const FriendsModal: React.FC<FriendsModalProps> = ({ isOpen, onClose, onSelectRoom }) => {
  const [activeTab, setActiveTab] = useState<'friends' | 'add'>('friends');
  const [friends, setFriends] = useState<Friend[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<User[]>([]);
  const [sentRequests, setSentRequests] = useState<Set<string>>(new Set());
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (isOpen && activeTab === 'friends') {
      loadFriends();
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

  const handleSearchUsers = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
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
      setSentRequests((prev) => new Set(prev).add(friendId));
    } catch (err) {
      console.error('Failed to send friend request:', err);
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

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm">
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative max-h-[85vh] flex flex-col">
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
        <div className="flex p-1 bg-slate-950/60 rounded-xl mb-4 border border-slate-800">
          <button
            onClick={() => setActiveTab('friends')}
            className={`flex-1 py-2 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'friends' ? 'bg-slate-800 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            My Friends ({friends.length})
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
              friends.map((f) => (
                <div
                  key={f.id}
                  className="flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800/80 hover:border-slate-700 transition-all"
                >
                  <div className="flex items-center gap-3">
                    <Avatar name={f.friendDisplayName} size="md" />
                    <div>
                      <h4 className="text-sm font-bold text-white leading-tight">{f.friendDisplayName}</h4>
                      <span className="text-xs text-slate-400 font-mono">@{f.friendUsername}</span>
                    </div>
                  </div>
                  <button
                    onClick={() => handleStartDM(f.friendId)}
                    className="px-3 py-1.5 bg-blue-600/10 hover:bg-blue-600/20 border border-blue-500/20 text-blue-400 text-xs font-semibold rounded-xl flex items-center gap-1.5 transition-colors"
                  >
                    <MessageSquare className="w-3.5 h-3.5" />
                    <span>Message</span>
                  </button>
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
                        <Avatar name={u.displayName} size="md" />
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
    </div>
  );
};
