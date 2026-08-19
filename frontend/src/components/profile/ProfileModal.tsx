import React, { useRef, useState } from 'react';
import type { User } from '../../types';
import { userApi } from '../../services/api';
import { X, User as UserIcon, Lock, Trash2, Check, Loader2, Camera, Upload } from 'lucide-react';
import { Avatar } from '../ui/Avatar';

interface ProfileModalProps {
  user: User;
  isOpen: boolean;
  onClose: () => void;
  onProfileUpdated: (user: User) => void;
  onLogout: () => void;
}

const MAX_AVATAR_SIZE = 5 * 1024 * 1024;
const MIN_AVATAR_DIM = 64;
const MAX_AVATAR_DIM = 2048;
const AVATAR_ACCEPT = 'image/jpeg,image/png,image/gif';

export const ProfileModal: React.FC<ProfileModalProps> = ({
  user,
  isOpen,
  onClose,
  onProfileUpdated,
  onLogout,
}) => {
  const [displayName, setDisplayName] = useState(user.displayName);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  // Avatar upload state
  const [pendingAvatar, setPendingAvatar] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const avatarInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    setIsSubmitting(true);

    try {
      const updated = await userApi.updateProfile({
        displayName: displayName.trim(),
        currentPassword: currentPassword ? currentPassword : undefined,
        newPassword: newPassword ? newPassword : undefined,
      });

      onProfileUpdated(updated);
      setSuccessMsg('Profile updated successfully');
      setCurrentPassword('');
      setNewPassword('');
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || err.message || 'Failed to update profile');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleAvatarSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    if (!AVATAR_ACCEPT.split(',').includes(file.type)) {
      setErrorMsg('Please choose a JPEG, PNG or GIF image.');
      return;
    }
    if (file.size > MAX_AVATAR_SIZE) {
      setErrorMsg('Avatar must be smaller than 5MB.');
      return;
    }

    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      if (img.naturalWidth < MIN_AVATAR_DIM || img.naturalHeight < MIN_AVATAR_DIM) {
        URL.revokeObjectURL(url);
        setErrorMsg(`Image must be at least ${MIN_AVATAR_DIM}x${MIN_AVATAR_DIM} pixels.`);
        return;
      }
      if (img.naturalWidth > MAX_AVATAR_DIM || img.naturalHeight > MAX_AVATAR_DIM) {
        URL.revokeObjectURL(url);
        setErrorMsg(`Image must be at most ${MAX_AVATAR_DIM}x${MAX_AVATAR_DIM} pixels.`);
        return;
      }
      if (avatarPreview) URL.revokeObjectURL(avatarPreview);
      setErrorMsg(null);
      setPendingAvatar(file);
      setAvatarPreview(url);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      setErrorMsg('Could not read that image file. Use a valid JPEG, PNG or GIF.');
    };
    img.src = url;
  };

  const cancelAvatar = () => {
    if (avatarPreview) URL.revokeObjectURL(avatarPreview);
    setPendingAvatar(null);
    setAvatarPreview(null);
    setErrorMsg(null);
  };

  const handleUploadAvatar = async () => {
    if (!pendingAvatar) return;
    setErrorMsg(null);
    setSuccessMsg(null);
    setIsUploadingAvatar(true);
    try {
      const updated = await userApi.uploadAvatar(pendingAvatar);
      onProfileUpdated(updated);
      setSuccessMsg('Profile picture updated');
      cancelAvatar();
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to upload avatar');
    } finally {
      setIsUploadingAvatar(false);
    }
  };

  const handleDeleteAccount = async () => {
    try {
      await userApi.deleteAccount();
      onLogout();
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Failed to delete account');
    }
  };

  const currentAvatar = avatarPreview ?? user.avatarUrl ?? '';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm">
      <div className="w-full max-w-lg bg-slate-900 border border-slate-800 rounded-3xl p-6 shadow-2xl relative max-h-[90vh] overflow-y-auto">
        <button
          onClick={onClose}
          className="absolute top-5 right-5 text-slate-400 hover:text-white p-1 rounded-xl hover:bg-slate-800 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-4 mb-6">
          <Avatar name={displayName} src={currentAvatar} size="lg" />
          <div>
            <h2 className="text-xl font-bold text-white tracking-tight">{user.displayName}</h2>
            <span className="text-xs text-slate-400 font-mono">@{user.username}</span>
            {user.role && (
              <span className="ml-2 px-2 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wider bg-blue-500/10 text-blue-400 border border-blue-500/20">
                {user.role}
              </span>
            )}
          </div>
        </div>

        {successMsg && (
          <div className="mb-4 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs flex items-center gap-2">
            <Check className="w-4 h-4" />
            <span>{successMsg}</span>
          </div>
        )}

        {errorMsg && (
          <div className="mb-4 p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
            {errorMsg}
          </div>
        )}

        <form onSubmit={handleUpdate} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
              Display Name
            </label>
            <div className="relative">
              <UserIcon className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
              <input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5">
              Profile Picture
            </label>
            <div className="flex items-center gap-4">
              <Avatar name={displayName} src={currentAvatar} size="lg" />
              <input
                ref={avatarInputRef}
                type="file"
                accept={AVATAR_ACCEPT}
                onChange={handleAvatarSelect}
                className="hidden"
              />
              {pendingAvatar ? (
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={handleUploadAvatar}
                    disabled={isUploadingAvatar}
                    className="px-3 py-2 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl flex items-center gap-1.5 transition-colors disabled:opacity-50"
                  >
                    {isUploadingAvatar ? (
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    ) : (
                      <Upload className="w-3.5 h-3.5" />
                    )}
                    <span>{isUploadingAvatar ? 'Uploading...' : 'Upload'}</span>
                  </button>
                  <button
                    type="button"
                    onClick={cancelAvatar}
                    disabled={isUploadingAvatar}
                    className="px-3 py-2 text-slate-400 hover:text-white text-xs font-medium transition-colors disabled:opacity-50"
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => avatarInputRef.current?.click()}
                  className="px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold rounded-xl flex items-center gap-1.5 transition-colors"
                >
                  <Camera className="w-3.5 h-3.5" />
                  <span>Change Photo</span>
                </button>
              )}
            </div>
            <p className="text-[10px] text-slate-500 mt-1.5">
              JPEG, PNG or GIF. Between {MIN_AVATAR_DIM}x{MIN_AVATAR_DIM} and {MAX_AVATAR_DIM}x{MAX_AVATAR_DIM} pixels, max 5MB.
            </p>
          </div>

          <hr className="border-slate-800 my-4" />

          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2">Change Password</h3>

          <div>
            <label className="block text-xs font-semibold text-slate-400 mb-1">Current Password</label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                placeholder="Required if changing password"
                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-400 mb-1">New Password</label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Min 6 characters"
                className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
              />
            </div>
          </div>

          <div className="flex justify-end gap-3 pt-4">
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm rounded-xl shadow-lg shadow-blue-600/20 flex items-center gap-2 transition-all disabled:opacity-50"
            >
              {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : 'Save Changes'}
            </button>
          </div>
        </form>

        <hr className="border-slate-800 my-6" />

        <div className="bg-red-500/5 border border-red-500/10 rounded-2xl p-4 flex items-center justify-between">
          <div>
            <h4 className="text-sm font-bold text-red-400">Delete Account</h4>
            <p className="text-xs text-slate-400">Permanently disable your account</p>
          </div>
          {showDeleteConfirm ? (
            <div className="flex items-center gap-2">
              <button
                onClick={handleDeleteAccount}
                className="px-3 py-1.5 bg-red-600 hover:bg-red-500 text-white text-xs font-bold rounded-lg"
              >
                Confirm Delete
              </button>
              <button
                onClick={() => setShowDeleteConfirm(false)}
                className="px-2 py-1.5 text-slate-400 hover:text-white text-xs"
              >
                Cancel
              </button>
            </div>
          ) : (
            <button
              onClick={() => setShowDeleteConfirm(true)}
              className="px-3 py-2 bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 text-xs font-semibold rounded-xl flex items-center gap-1.5 transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Delete</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};