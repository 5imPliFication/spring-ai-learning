import React, { useState } from 'react';
import type { Room, User } from '../../types';
import { roomApi } from '../../services/api';
import { X, Trash2, Settings, UserMinus, AlertTriangle } from 'lucide-react';

interface RoomSettingsModalProps {
  room: Room | null;
  currentUser: User;
  isOpen: boolean;
  onClose: () => void;
  onRoomDeleted: (roomId: string) => void;
}

export const RoomSettingsModal: React.FC<RoomSettingsModalProps> = ({
  room,
  currentUser,
  isOpen,
  onClose,
  onRoomDeleted,
}) => {
  const [targetKickId, setTargetKickId] = useState('');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  if (!isOpen || !room) return null;

  const isOwner = room.createdBy === currentUser.id;
  const isAdmin = currentUser.role === 'ADMIN';

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

  const handleKickMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetKickId.trim()) return;
    setErrorMsg(null);
    setSuccessMsg(null);
    try {
      await roomApi.kickMember(room.id, targetKickId.trim());
      setSuccessMsg(`User ${targetKickId} kicked from room.`);
      setTargetKickId('');
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to kick user');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative">
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
        <p className="text-slate-400 text-xs mb-6">Manage <span className="text-white font-semibold">{room.name}</span></p>

        {errorMsg && (
          <div className="mb-4 p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
            {errorMsg}
          </div>
        )}

        {successMsg && (
          <div className="mb-4 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs">
            {successMsg}
          </div>
        )}

        {(isOwner || isAdmin) ? (
          <div className="space-y-6">
            {/* Kick Member Section */}
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
                Kick Member by User ID
              </label>
              <form onSubmit={handleKickMember} className="flex gap-2">
                <input
                  type="text"
                  value={targetKickId}
                  onChange={(e) => setTargetKickId(e.target.value)}
                  placeholder="Enter User ID to kick"
                  className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
                />
                <button
                  type="submit"
                  disabled={!targetKickId.trim()}
                  className="px-3 py-2 bg-amber-600/10 hover:bg-amber-600/20 border border-amber-500/20 text-amber-400 text-xs font-semibold rounded-xl flex items-center gap-1 transition-colors disabled:opacity-50"
                >
                  <UserMinus className="w-3.5 h-3.5" />
                  <span>Kick</span>
                </button>
              </form>
            </div>

            <hr className="border-slate-800" />

            {/* Danger Zone / Delete Room */}
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
        ) : (
          <div className="py-6 text-center text-slate-500 text-xs">
            Only the Room Owner or System Admins can manage room settings.
          </div>
        )}
      </div>
    </div>
  );
};
