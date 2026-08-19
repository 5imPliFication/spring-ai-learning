import React, { useState, useEffect } from 'react';
import type { AdminDashboardStats, User, Room, Message } from '../../types';
import { adminApi } from '../../services/api';
import { useAuth } from '../../context/AuthContext';
import {
  LayoutDashboard,
  MessageSquare,
  Users,
  Zap,
  ShieldAlert,
  ArrowLeft,
  RefreshCw,
  Loader2,
  Hash,
  ShieldOff,
} from 'lucide-react';

interface AdminDashboardProps {
  onClose: () => void;
}

export const AdminDashboard: React.FC<AdminDashboardProps> = ({ onClose }) => {
  const { user: authUser } = useAuth();

  // Hard frontend guard — if user is not ADMIN, deny access
  if (!authUser || authUser.role !== 'ADMIN') {
    return (
      <div className="h-screen w-screen bg-slate-950 flex items-center justify-center p-6">
        <div className="bg-slate-900 border border-red-500/20 rounded-3xl p-8 max-w-md text-center shadow-2xl">
          <div className="w-16 h-16 rounded-2xl bg-red-500/10 border border-red-500/20 flex items-center justify-center mx-auto mb-4">
            <ShieldOff className="w-8 h-8 text-red-400" />
          </div>
          <h2 className="text-xl font-bold text-white mb-2">Access Denied</h2>
          <p className="text-sm text-slate-400 mb-6">You do not have administrator privileges to access this console.</p>
          <button
            onClick={onClose}
            className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-xl shadow-lg shadow-blue-600/20 transition-all"
          >
            Return to Chat
          </button>
        </div>
      </div>
    );
  }

  const [activeTab, setActiveTab] = useState<'overview' | 'users' | 'rooms' | 'messages'>('overview');
  const [stats, setStats] = useState<AdminDashboardStats | null>(null);
  const [users, setUsers] = useState<User[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, [activeTab]);

  const loadData = async () => {
    setIsLoading(true);
    try {
      if (activeTab === 'overview') {
        const data = await adminApi.getDashboardStats();
        setStats(data);
      } else if (activeTab === 'users') {
        const list = await adminApi.getUsers();
        setUsers(list);
      } else if (activeTab === 'rooms') {
        const list = await adminApi.getRooms();
        setRooms(list);
      } else if (activeTab === 'messages') {
        const list = await adminApi.getMessages();
        setMessages(list);
      }
    } catch (err) {
      console.error('Failed to load admin data:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDeleteUser = async (userId: string, hard: boolean) => {
    try {
      await adminApi.deleteUser(userId, hard);
      loadData();
    } catch (err) {
      console.error('Failed to delete user:', err);
    }
  };

  const handleDeleteRoom = async (roomId: string, hard: boolean) => {
    try {
      await adminApi.deleteRoom(roomId, hard);
      loadData();
    } catch (err) {
      console.error('Failed to delete room:', err);
    }
  };

  const handleDeleteMessage = async (messageId: number, hard: boolean) => {
    try {
      await adminApi.deleteMessage(messageId, hard);
      loadData();
    } catch (err) {
      console.error('Failed to delete message:', err);
    }
  };

  return (
    <div className="h-screen w-screen bg-slate-950 text-slate-100 flex flex-col overflow-hidden">
      {/* Top Header */}
      <header className="h-16 border-b border-slate-800 bg-slate-900/80 px-6 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors flex items-center gap-1.5 text-xs font-semibold"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Return to Chat</span>
          </button>
          <div className="h-4 w-px bg-slate-800" />
          <h1 className="text-lg font-bold text-white tracking-tight flex items-center gap-2">
            <ShieldAlert className="w-5 h-5 text-emerald-400" />
            Admin Dashboard & Management
          </h1>
        </div>

        <button
          onClick={loadData}
          className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors flex items-center gap-1.5 text-xs font-medium"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          <span>Refresh</span>
        </button>
      </header>

      {/* Admin Navigation Bar */}
      <div className="border-b border-slate-800 bg-slate-900/40 px-6 flex gap-2 pt-2">
        <button
          onClick={() => setActiveTab('overview')}
          className={`px-4 py-3 text-xs font-semibold rounded-t-xl transition-all flex items-center gap-2 border-b-2 ${
            activeTab === 'overview'
              ? 'border-emerald-400 text-white bg-slate-900/80'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <LayoutDashboard className="w-4 h-4" />
          <span>Analytics Overview</span>
        </button>

        <button
          onClick={() => setActiveTab('users')}
          className={`px-4 py-3 text-xs font-semibold rounded-t-xl transition-all flex items-center gap-2 border-b-2 ${
            activeTab === 'users'
              ? 'border-emerald-400 text-white bg-slate-900/80'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Users className="w-4 h-4" />
          <span>Users Management</span>
        </button>

        <button
          onClick={() => setActiveTab('rooms')}
          className={`px-4 py-3 text-xs font-semibold rounded-t-xl transition-all flex items-center gap-2 border-b-2 ${
            activeTab === 'rooms'
              ? 'border-emerald-400 text-white bg-slate-900/80'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <Hash className="w-4 h-4" />
          <span>Rooms Management</span>
        </button>

        <button
          onClick={() => setActiveTab('messages')}
          className={`px-4 py-3 text-xs font-semibold rounded-t-xl transition-all flex items-center gap-2 border-b-2 ${
            activeTab === 'messages'
              ? 'border-emerald-400 text-white bg-slate-900/80'
              : 'border-transparent text-slate-400 hover:text-slate-200'
          }`}
        >
          <MessageSquare className="w-4 h-4" />
          <span>Messages Management</span>
        </button>
      </div>

      {/* Main Content Area */}
      <main className="flex-1 p-6 overflow-y-auto bg-slate-950">
        {isLoading ? (
          <div className="h-full flex items-center justify-center text-slate-500">
            <Loader2 className="w-8 h-8 animate-spin text-emerald-400 mb-2" />
          </div>
        ) : (
          <>
            {/* OVERVIEW TAB */}
            {activeTab === 'overview' && stats && (
              <div className="space-y-6 max-w-6xl mx-auto">
                <h2 className="text-sm font-bold uppercase tracking-wider text-slate-400 mb-4">
                  System Analytics & Usage Metrics
                </h2>

                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                  {/* Messages Card */}
                  <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg">
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-xs font-semibold text-slate-400">Messages Sent</span>
                      <MessageSquare className="w-4 h-4 text-blue-400" />
                    </div>
                    <div className="text-2xl font-bold text-white mb-2">{stats.dailyMessages}</div>
                    <div className="text-[11px] text-slate-400 space-y-0.5">
                      <div>7 Days: <span className="text-slate-200 font-semibold">{stats.weeklyMessages}</span></div>
                      <div>30 Days: <span className="text-slate-200 font-semibold">{stats.monthlyMessages}</span></div>
                    </div>
                  </div>

                  {/* Token Consumption Card */}
                  <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg">
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-xs font-semibold text-slate-400">AI Tokens Used</span>
                      <Zap className="w-4 h-4 text-amber-400" />
                    </div>
                    <div className="text-2xl font-bold text-amber-400 mb-2">
                      {stats.dailyTokensUsed.toLocaleString()}
                    </div>
                    <div className="text-[11px] text-slate-400 space-y-0.5">
                      <div>7 Days: <span className="text-slate-200 font-semibold">{stats.weeklyTokensUsed.toLocaleString()}</span></div>
                      <div>30 Days: <span className="text-slate-200 font-semibold">{stats.monthlyTokensUsed.toLocaleString()}</span></div>
                    </div>
                  </div>

                  {/* Active & Online Users Card */}
                  <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg">
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-xs font-semibold text-slate-400">User Activity</span>
                      <Users className="w-4 h-4 text-emerald-400" />
                    </div>
                    <div className="flex items-baseline gap-2 mb-2">
                      <span className="text-2xl font-bold text-emerald-400">{stats.onlineUsersCount}</span>
                      <span className="text-xs text-emerald-400 font-semibold">Online Now</span>
                    </div>
                    <div className="text-[11px] text-slate-400">
                      Active (7d): <span className="text-slate-200 font-semibold">{stats.activeUsersCount}</span>
                    </div>
                  </div>

                  {/* Total Rooms Card */}
                  <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-lg">
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-xs font-semibold text-slate-400">Chat Rooms</span>
                      <Hash className="w-4 h-4 text-purple-400" />
                    </div>
                    <div className="text-2xl font-bold text-white mb-2">{stats.totalRoomsCount}</div>
                    <div className="text-[11px] text-slate-400">Active rooms on server</div>
                  </div>
                </div>
              </div>
            )}

            {/* USERS MANAGEMENT TAB */}
            {activeTab === 'users' && (
              <div className="max-w-6xl mx-auto space-y-4">
                <h2 className="text-sm font-bold uppercase tracking-wider text-slate-400">All Registered Accounts</h2>
                <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] tracking-wider border-b border-slate-800">
                      <tr>
                        <th className="p-4">User ID</th>
                        <th className="p-4">Username</th>
                        <th className="p-4">Display Name</th>
                        <th className="p-4">Role</th>
                        <th className="p-4">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 text-slate-200">
                      {users.map((u) => (
                        <tr key={u.id} className="hover:bg-slate-800/40">
                          <td className="p-4 font-mono text-slate-400">{u.id}</td>
                          <td className="p-4 font-semibold">{u.username}</td>
                          <td className="p-4">{u.displayName}</td>
                          <td className="p-4">
                            <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                              u.role === 'ADMIN' ? 'bg-amber-500/10 text-amber-400 border border-amber-500/20' : 'bg-slate-800 text-slate-300'
                            }`}>
                              {u.role || 'USER'}
                            </span>
                          </td>
                          <td className="p-4 space-x-2">
                            <button
                              onClick={() => handleDeleteUser(u.id, false)}
                              className="px-2.5 py-1 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/20 rounded-lg font-semibold"
                            >
                              Soft Delete
                            </button>
                            <button
                              onClick={() => handleDeleteUser(u.id, true)}
                              className="px-2.5 py-1 bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 rounded-lg font-semibold"
                            >
                              Hard Delete
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* ROOMS MANAGEMENT TAB */}
            {activeTab === 'rooms' && (
              <div className="max-w-6xl mx-auto space-y-4">
                <h2 className="text-sm font-bold uppercase tracking-wider text-slate-400">All Chat Rooms</h2>
                <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] tracking-wider border-b border-slate-800">
                      <tr>
                        <th className="p-4">Room ID</th>
                        <th className="p-4">Name</th>
                        <th className="p-4">Type</th>
                        <th className="p-4">Protected</th>
                        <th className="p-4">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 text-slate-200">
                      {rooms.map((r) => (
                        <tr key={r.id} className="hover:bg-slate-800/40">
                          <td className="p-4 font-mono text-slate-400">{r.id}</td>
                          <td className="p-4 font-semibold">{r.name}</td>
                          <td className="p-4 font-mono text-slate-400">{r.type}</td>
                          <td className="p-4">
                            {r.isProtected ? (
                              <span className="text-amber-400 font-semibold">Protected</span>
                            ) : (
                              <span className="text-slate-500">Public</span>
                            )}
                          </td>
                          <td className="p-4 space-x-2">
                            <button
                              onClick={() => handleDeleteRoom(r.id, false)}
                              className="px-2.5 py-1 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/20 rounded-lg font-semibold"
                            >
                              Soft Delete
                            </button>
                            <button
                              onClick={() => handleDeleteRoom(r.id, true)}
                              className="px-2.5 py-1 bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 rounded-lg font-semibold"
                            >
                              Hard Delete
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* MESSAGES MANAGEMENT TAB */}
            {activeTab === 'messages' && (
              <div className="max-w-6xl mx-auto space-y-4">
                <h2 className="text-sm font-bold uppercase tracking-wider text-slate-400">All Chat Messages</h2>
                <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-950 text-slate-400 uppercase text-[10px] tracking-wider border-b border-slate-800">
                      <tr>
                        <th className="p-4">Message ID</th>
                        <th className="p-4">Sender</th>
                        <th className="p-4">Content</th>
                        <th className="p-4">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/60 text-slate-200">
                      {messages.map((m) => (
                        <tr key={m.id} className="hover:bg-slate-800/40">
                          <td className="p-4 font-mono text-slate-400">{m.id}</td>
                          <td className="p-4 font-semibold">{m.senderName}</td>
                          <td className="p-4 max-w-xs truncate">{m.content}</td>
                          <td className="p-4 space-x-2">
                            <button
                              onClick={() => handleDeleteMessage(m.id, false)}
                              className="px-2.5 py-1 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/20 rounded-lg font-semibold"
                            >
                              Soft Delete
                            </button>
                            <button
                              onClick={() => handleDeleteMessage(m.id, true)}
                              className="px-2.5 py-1 bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 rounded-lg font-semibold"
                            >
                              Hard Delete
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
};
