import React, { useState, useEffect, useCallback } from 'react';
import type { Room, User, RoomMember } from '../../types';
import { roomApi } from '../../services/api';
import { Avatar } from '../ui/Avatar';
import { X, Trash2, Settings, UserMinus, AlertTriangle, Pencil, Lock, Users, KeyRound, Unlock, Check, Loader2, Copy, Link2 } from 'lucide-react';

interface RoomSettingsModalProps {
  room: Room | null;
  currentUser: User;
  isOpen: boolean;
  onClose: () => void;
  onRoomDeleted: (roomId: string) => void;
  onRoomUpdated?: () => void;
}

export const RoomSettingsModal: React.FC<RoomSettingsModalProps> = ({
  room,
  currentUser,
  isOpen,
  onClose,
  onRoomDeleted,
  onRoomUpdated,
}) => {
  const [section, setSection] = useState<'general' | 'security' | 'members'>('general');
  const [roomName, setRoomName] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [isProtected, setIsProtected] = useState(false);
  const [members, setMembers] = useState<RoomMember[]>([]);
  const [isMembersLoading, setIsMembersLoading] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const loadMembers = useCallback(async () => {
    if (!room) return;
    setIsMembersLoading(true);
    try {
      const data = await roomApi.getMembers(room.id);
      setMembers(data);
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to load members');
    } finally {
      setIsMembersLoading(false);
    }
  }, [room]);

  useEffect(() => {
    if (isOpen && room) {
      setRoomName(room.name);
      setNewPassword('');
      setIsProtected(!!room.isProtected);
      setErrorMsg(null);
      setSuccessMsg(null);
      setShowDeleteConfirm(false);
      const isManager = room.createdBy === currentUser.id || currentUser.role === 'ADMIN';
      setSection(isManager ? 'general' : 'members');
      loadMembers();
    }
  }, [isOpen, room, loadMembers, currentUser.id, currentUser.role]);

  if (!isOpen || !room) return null;

  const isOwner = room.createdBy === currentUser.id;
  const isAdmin = currentUser.role === 'ADMIN';
  const canManage = isOwner || isAdmin;

  const sections: { id: 'general' | 'security' | 'members'; label: string; icon: React.ReactNode }[] = [];
  if (canManage) {
    sections.push(
      { id: 'general', label: 'General', icon: <Pencil className="w-3.5 h-3.5" /> },
      { id: 'security', label: 'Security', icon: <Lock className="w-3.5 h-3.5" /> }
    );
  }
  sections.push({ id: 'members', label: `Members (${members.length})`, icon: <Users className="w-3.5 h-3.5" /> });

  const handleRename = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!roomName.trim() || roomName.trim() === room.name) return;
    setErrorMsg(null);
    setSuccessMsg(null);
    setIsSaving(true);
    try {
      await roomApi.updateRoom(room.id, { name: roomName.trim() });
      onRoomUpdated?.();
      setSuccessMsg('Room name updated.');
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to rename room');
    } finally {
      setIsSaving(false);
    }
  };

  const handleSetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPassword.trim()) return;
    setErrorMsg(null);
    setSuccessMsg(null);
    setIsSaving(true);
    try {
      await roomApi.updateRoom(room.id, { password: newPassword.trim() });
      setIsProtected(true);
      setNewPassword('');
      onRoomUpdated?.();
      setSuccessMsg('Room password updated.');
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to update room password');
    } finally {
      setIsSaving(false);
    }
  };

  const handleRemovePassword = async () => {
    setErrorMsg(null);
    setSuccessMsg(null);
    setIsSaving(true);
    try {
      await roomApi.updateRoom(room.id, { password: '' });
      setIsProtected(false);
      setNewPassword('');
      onRoomUpdated?.();
      setSuccessMsg('Room password removed.');
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to remove room password');
    } finally {
      setIsSaving(false);
    }
  };

  const handleKickMember = async (userId: string) => {
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      await roomApi.kickMember(room.id, userId);
      setMembers((prev) => prev.filter((m) => m.userId !== userId));
      setSuccessMsg('Member kicked from room.');
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to kick member');
    }
  };

  const handleCopyInvite = async () => {
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      const { url } = await roomApi.getInviteLink(room.id);
      await navigator.clipboard.writeText(url);
      setSuccessMsg('Invite link copied to clipboard.');
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to generate invite link');
    }
  };

  const handleDeleteRoom = async () => {
    setErrorMsg(null);
    try {
      await roomApi.deleteRoom(room.id);
      onRoomDeleted(room.id);
      onClose();
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to delete room');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm">
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative flex flex-col max-h-[85vh]">
        <button
          onClick={onClose}
          className="absolute top-5 right-5 text-slate-400 hover:text-white p-1 rounded-xl hover:bg-slate-800 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        <h2 className="text-xl font-bold text-white tracking-tight mb-1 flex items-center gap-2">
          <Settings className="w-5 h-5 text-blue-400" />
          Room Settings
        </h2>
        <p className="text-slate-400 text-xs mb-4">Manage <span className="text-white font-semibold">{room.name}</span></p>

        {room.isPrivate && (
          <div className="flex items-center gap-2 p-3 mb-4 rounded-2xl bg-violet-500/10 border border-violet-500/20">
            <div className="flex-1">
              <h4 className="text-xs font-bold text-violet-300 flex items-center gap-1.5">
                <Link2 className="w-3.5 h-3.5" />
                Private Room Invite Link
              </h4>
              <p className="text-[11px] text-slate-400 mt-0.5">
                Anyone with this link can join this room.
              </p>
            </div>
            <button
              onClick={handleCopyInvite}
              className="px-3 py-2 bg-violet-600 hover:bg-violet-500 text-white text-xs font-semibold rounded-xl flex items-center gap-1.5 transition-colors"
            >
              <Copy className="w-3.5 h-3.5" />
              <span>Copy Link</span>
            </button>
          </div>
        )}

        <>
            <div className="flex gap-1.5 mb-4 p-1 bg-slate-950/60 border border-slate-800 rounded-xl">
              {sections.map((s) => (
                <button
                  key={s.id}
                  onClick={() => setSection(s.id)}
                  className={`flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    section === s.id
                      ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
                  }`}
                >
                  {s.icon}
                  <span>{s.label}</span>
                </button>
              ))}
            </div>

            {errorMsg && (
              <div className="mb-3 p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
                {errorMsg}
              </div>
            )}

            {successMsg && (
              <div className="mb-3 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs">
                {successMsg}
              </div>
            )}

            <div className="flex-1 overflow-y-auto pr-1 space-y-4 min-h-0">
              {section === 'general' && (
                <div className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
                      Room Name
                    </label>
                    <form onSubmit={handleRename} className="flex gap-2">
                      <input
                        type="text"
                        value={roomName}
                        onChange={(e) => setRoomName(e.target.value)}
                        className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
                      />
                      <button
                        type="submit"
                        disabled={isSaving || !roomName.trim() || roomName.trim() === room.name}
                        className="px-3 py-2 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl flex items-center gap-1 transition-colors disabled:opacity-50"
                      >
                        {isSaving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Check className="w-3.5 h-3.5" />}
                        <span>Save</span>
                      </button>
                    </form>
                  </div>

                  <div className="bg-red-500/5 border border-red-500/10 rounded-2xl p-4">
                    <div className="flex items-center gap-2 mb-2">
                      <AlertTriangle className="w-4 h-4 text-red-400" />
                      <h4 className="text-sm font-bold text-red-400">Delete Room</h4>
                    </div>
                    <p className="text-xs text-slate-400 mb-4">
                      This action will delete the room and all its messages.
                    </p>

                    {showDeleteConfirm ? (
                      <div className="flex items-center gap-2">
                        <button
                          onClick={handleDeleteRoom}
                          className="px-4 py-2 bg-red-600 hover:bg-red-500 text-white text-xs font-bold rounded-xl shadow-lg shadow-red-600/20"
                        >
                          Confirm Delete
                        </button>
                        <button
                          onClick={() => setShowDeleteConfirm(false)}
                          className="px-3 py-2 text-slate-400 hover:text-white text-xs font-medium"
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setShowDeleteConfirm(true)}
                        className="w-full py-2.5 bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 text-xs font-semibold rounded-xl flex items-center justify-center gap-2 transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                        <span>Delete Room</span>
                      </button>
                    )}
                  </div>
                </div>
              )}

              {section === 'security' && (
                <div className="space-y-4">
                  {isProtected ? (
                    <>
                      <div className="flex items-center gap-2 p-3 rounded-2xl bg-amber-500/10 border border-amber-500/20">
                        <Lock className="w-4 h-4 text-amber-400 shrink-0" />
                        <p className="text-xs text-amber-300">
                          This room is password protected. Joining requires the password.
                        </p>
                      </div>
                      <form onSubmit={handleSetPassword} className="space-y-1.5">
                        <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
                          Change Password
                        </label>
                        <div className="flex gap-2">
                          <input
                            type="password"
                            value={newPassword}
                            onChange={(e) => setNewPassword(e.target.value)}
                            placeholder="New room password"
                            className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
                          />
                          <button
                            type="submit"
                            disabled={isSaving || !newPassword.trim()}
                            className="px-3 py-2 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl transition-colors disabled:opacity-50"
                          >
                            Update
                          </button>
                        </div>
                      </form>
                      <button
                        onClick={handleRemovePassword}
                        disabled={isSaving}
                        className="w-full flex items-center justify-center gap-2 px-3 py-2.5 bg-amber-600/10 hover:bg-amber-600/20 border border-amber-500/20 text-amber-400 text-xs font-semibold rounded-xl transition-colors disabled:opacity-50"
                      >
                        <Unlock className="w-3.5 h-3.5" />
                        Remove Password Protection
                      </button>
                    </>
                  ) : (
                    <form onSubmit={handleSetPassword} className="space-y-1.5">
                      <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                        <KeyRound className="w-3.5 h-3.5" />
                        Enable Password Protection
                      </label>
                      <div className="flex gap-2">
                        <input
                          type="password"
                          value={newPassword}
                          onChange={(e) => setNewPassword(e.target.value)}
                          placeholder="Set a room password"
                          className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
                        />
                        <button
                          type="submit"
                          disabled={isSaving || !newPassword.trim()}
                          className="px-3 py-2 bg-amber-600 hover:bg-amber-500 text-white text-xs font-semibold rounded-xl transition-colors disabled:opacity-50"
                        >
                          Enable
                        </button>
                      </div>
                    </form>
                  )}
                </div>
              )}

              {section === 'members' && (
                <div className="space-y-1.5">
                  {isMembersLoading ? (
                    <div className="py-8 text-center text-slate-500">
                      <Loader2 className="w-5 h-5 animate-spin mx-auto mb-2 text-blue-500" />
                    </div>
                  ) : members.length === 0 ? (
                    <div className="py-8 text-center text-slate-500 text-xs">No members in this room.</div>
                  ) : (
                    members.map((m) => {
                      const isOwnerRow = m.role === 'OWNER';
                      const isSelf = m.userId === currentUser.id;
                      const canKick = canManage && !isSelf && !isOwnerRow;
                      return (
                        <div
                          key={m.userId}
                          className="flex items-center justify-between p-2.5 rounded-xl bg-slate-950/60 border border-slate-800/80"
                        >
                          <div className="flex items-center gap-2.5 min-w-0">
                            <Avatar name={m.displayName} src={m.avatarUrl} size="sm" />
                            <div className="min-w-0">
                              <div className="flex items-center gap-1.5">
                                <span className="text-xs font-semibold text-white truncate">{m.displayName}</span>
                                {isOwnerRow && (
                                  <span className="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20 font-bold uppercase tracking-wide">
                                    Owner
                                  </span>
                                )}
                                {isSelf && (
                                  <span className="text-[9px] px-1.5 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20 font-bold uppercase tracking-wide">
                                    You
                                  </span>
                                )}
                              </div>
                              <span className="text-[10px] text-slate-400 font-mono truncate">@{m.username}</span>
                            </div>
                          </div>
                          {canKick && (
                            <button
                              onClick={() => handleKickMember(m.userId)}
                              title={`Kick ${m.displayName}`}
                              className="p-1.5 rounded-lg bg-amber-600/10 hover:bg-amber-600/20 text-amber-400 transition-colors"
                            >
                              <UserMinus className="w-3.5 h-3.5" />
                            </button>
                          )}
                        </div>
                      );
                    })
                  )}
                </div>
              )}
            </div>
          </>
      </div>
    </div>
  );
};
