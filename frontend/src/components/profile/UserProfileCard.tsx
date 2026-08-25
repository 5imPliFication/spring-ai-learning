import React, { useEffect, useState } from 'react';
import type { User } from '../../types';
import { userApi } from '../../services/api';
import { ArrowLeft, Loader2, MapPin, VenusAndMars, Phone, Link as LinkIcon, Calendar } from 'lucide-react';
import { Avatar } from '../ui/Avatar';

interface UserProfileCardProps {
  userId: string;
  onClose: () => void;
}

export const UserProfileCard: React.FC<UserProfileCardProps> = ({ userId, onClose }) => {
  const [profile, setProfile] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!userId) return;
    setLoading(true);
    setError(null);
    setProfile(null);
    userApi
      .getPublicProfile(userId)
      .then(setProfile)
      .catch((err: any) => setError(err.response?.data?.message || 'Failed to load profile'))
      .finally(() => setLoading(false));
  }, [userId]);

  const joined = profile?.createdAt
    ? new Date(profile.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
    : null;

  return (
    <div className="flex-1 flex flex-col h-full overflow-y-auto">
      {/* Top bar */}
      <div className="flex items-center gap-3 px-6 py-4 border-b border-slate-800">
        <button
          onClick={onClose}
          className="p-1.5 rounded-lg hover:bg-slate-800 text-slate-400 hover:text-white transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <span className="text-sm font-semibold text-white">Profile</span>
      </div>

      <div className="flex-1 flex items-start justify-center p-6">
        <div className="w-full max-w-md space-y-5">
          {loading && (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="w-6 h-6 text-slate-400 animate-spin" />
            </div>
          )}

          {error && (
            <div className="py-12 text-center text-sm text-red-400">{error}</div>
          )}

          {profile && (
            <>
              {/* Header */}
              <div className="flex items-center gap-4">
                <Avatar name={profile.displayName} src={profile.avatarUrl} size="lg" />
                <div className="min-w-0">
                  <h2 className="text-xl font-bold text-white tracking-tight truncate">{profile.displayName}</h2>
                  <span className="text-xs text-slate-400 font-mono">@{profile.username}</span>
                  {profile.role && (
                    <span className="ml-2 px-2 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wider bg-blue-500/10 text-blue-400 border border-blue-500/20">
                      {profile.role}
                    </span>
                  )}
                </div>
              </div>

              {/* Bio */}
              {profile.bio && (
                <p className="text-sm text-slate-300 leading-relaxed whitespace-pre-line">{profile.bio}</p>
              )}

              {/* Details */}
              {(profile.location || profile.gender || profile.phone || joined) && (
                <div className="space-y-2">
                  {profile.location && (
                    <div className="flex items-center gap-2 text-sm text-slate-300">
                      <MapPin className="w-4 h-4 text-slate-500 shrink-0" />
                      <span>{profile.location}</span>
                    </div>
                  )}
                  {profile.gender && (
                    <div className="flex items-center gap-2 text-sm text-slate-300">
                      <VenusAndMars className="w-4 h-4 text-slate-500 shrink-0" />
                      <span>{profile.gender}</span>
                    </div>
                  )}
                  {profile.phone && (
                    <div className="flex items-center gap-2 text-sm text-slate-300">
                      <Phone className="w-4 h-4 text-slate-500 shrink-0" />
                      <span>{profile.phone}</span>
                    </div>
                  )}
                  {joined && (
                    <div className="flex items-center gap-2 text-sm text-slate-400">
                      <Calendar className="w-4 h-4 text-slate-500 shrink-0" />
                      <span>Joined {joined}</span>
                    </div>
                  )}
                </div>
              )}

              {/* Links */}
              {profile.links && profile.links.length > 0 && (
                <div className="space-y-1.5">
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400">Links</h4>
                  {profile.links.map((link, idx) => (
                    <a
                      key={link.id ?? idx}
                      href={link.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex items-center gap-2 text-sm text-blue-400 hover:text-blue-300 transition-colors"
                    >
                      <LinkIcon className="w-3.5 h-3.5 shrink-0" />
                      <span className="truncate">{link.label}</span>
                    </a>
                  ))}
                </div>
              )}

              {/* Empty state */}
              {!profile.bio && !profile.location && !profile.gender && !profile.phone && (!profile.links || profile.links.length === 0) && (
                <p className="text-sm text-slate-500 text-center py-4">This user hasn't filled out their profile yet.</p>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};
