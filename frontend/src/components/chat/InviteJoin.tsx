import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router';
import type { Room } from '../../types';
import { roomApi } from '../../services/api';
import { Lock, Loader2, ArrowRight, Link2, Hash, AlertTriangle } from 'lucide-react';

export const InviteJoin: React.FC = () => {
  const { roomId } = useParams<{ roomId: string }>();
  const navigate = useNavigate();
  const [room, setRoom] = useState<Room | null>(null);
  const [password, setPassword] = useState('');
  const [needPassword, setNeedPassword] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isJoining, setIsJoining] = useState(false);

  useEffect(() => {
    if (!roomId) return;
    (async () => {
      try {
        const r = await roomApi.getRoom(roomId);
        setRoom(r);
        setNeedPassword(!!r.isProtected);
      } catch (err: any) {
        setErrorMsg(err.response?.data?.message || 'Room not found');
      } finally {
        setIsLoading(false);
      }
    })();
  }, [roomId]);

  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!roomId) return;
    setIsJoining(true);
    setErrorMsg(null);
    try {
      await roomApi.joinRoom(roomId, password.trim() || undefined);
      navigate(`/rooms/${roomId}`);
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Failed to join room';
      setErrorMsg(msg);
      if (msg.toLowerCase().includes('password')) setNeedPassword(true);
    } finally {
      setIsJoining(false);
    }
  };

  return (
    <div className="min-h-screen w-full bg-slate-950 flex items-center justify-center p-4 text-slate-100">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-3xl p-8 shadow-2xl">
        <div className="w-14 h-14 rounded-2xl bg-violet-500/10 border border-violet-500/20 text-violet-400 flex items-center justify-center mb-5">
          <Link2 className="w-7 h-7" />
        </div>

        <h2 className="text-xl font-bold text-white tracking-tight mb-1">Room Invitation</h2>

        {isLoading ? (
          <div className="py-10 text-center text-slate-500">
            <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2 text-blue-500" />
            <span className="text-xs">Looking up room...</span>
          </div>
        ) : errorMsg && !room ? (
          <div className="py-6">
            <div className="flex items-start gap-2.5 p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs mb-4">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{errorMsg}. The invite link may be invalid or the room was deleted.</span>
            </div>
            <button
              onClick={() => navigate('/')}
              className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold rounded-xl transition-colors"
            >
              Go to Home
            </button>
          </div>
        ) : (
          <div>
            <p className="text-slate-400 text-sm mb-6">
              You've been invited to join{' '}
              <span className="text-white font-semibold flex items-center gap-1.5 mt-1">
                <span className="w-6 h-6 rounded-lg bg-slate-800 flex items-center justify-center text-slate-400">
                  <Hash className="w-3.5 h-3.5" />
                </span>
                {room?.name}
              </span>
            </p>

            {needPassword ? (
              <form onSubmit={handleJoin} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5 flex items-center gap-1.5">
                    <Lock className="w-3.5 h-3.5 text-amber-400" />
                    Room Password
                  </label>
                  <input
                    type="password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Enter room password"
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
                  />
                </div>

                {errorMsg && (
                  <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
                    {errorMsg}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={isJoining || !password.trim()}
                  className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold rounded-xl flex items-center justify-center gap-2 transition-all disabled:opacity-50"
                >
                  {isJoining ? <Loader2 className="w-4 h-4 animate-spin" /> : (
                    <>
                      <span>Join Room</span>
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>
              </form>
            ) : (
              <div>
                {errorMsg && (
                  <div className="mb-4 p-3 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 text-xs">
                    {errorMsg}
                  </div>
                )}
                <button
                  onClick={handleJoin}
                  disabled={isJoining}
                  className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold rounded-xl flex items-center justify-center gap-2 transition-all disabled:opacity-50"
                >
                  {isJoining ? <Loader2 className="w-4 h-4 animate-spin" /> : (
                    <>
                      <span>Accept Invite & Join</span>
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};