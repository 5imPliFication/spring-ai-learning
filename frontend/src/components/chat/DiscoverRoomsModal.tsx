import React, { useState, useEffect } from 'react';
import type { Room } from '../../types';
import { roomApi } from '../../services/api';
import { Search, X, Hash, Lock, Globe, ArrowRight, Loader2, EyeOff } from 'lucide-react';

interface DiscoverRoomsModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectRoom: (roomId: string) => void;
}

export const DiscoverRoomsModal: React.FC<DiscoverRoomsModalProps> = ({
  isOpen,
  onClose,
  onSelectRoom,
}) => {
  const [query, setQuery] = useState('');
  const [allRooms, setAllRooms] = useState<Room[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (isOpen) {
      loadRooms();
    }
  }, [isOpen]);

  const loadRooms = async () => {
    setIsLoading(true);
    try {
      const data = await roomApi.getRooms();
      setAllRooms(data.filter((r) => r.type === 'GROUP'));
    } catch (err) {
      console.error('Failed to load rooms:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    try {
      if (!query.trim()) {
        await loadRooms();
      } else {
        const results = await roomApi.searchRooms(query.trim());
        setAllRooms(results.filter((r) => r.type === 'GROUP'));
      }
    } catch (err) {
      console.error('Room search error:', err);
    } finally {
      setIsLoading(false);
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

        <h2 className="text-xl font-bold text-white tracking-tight mb-1 flex items-center gap-2">
          <Globe className="w-5 h-5 text-blue-400" />
          Discover & Join Rooms
        </h2>
        <p className="text-slate-400 text-xs mb-4">Explore public chat rooms or enter passwords to join protected rooms</p>

        <form onSubmit={handleSearch} className="flex gap-2 mb-4">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search rooms by name..."
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
            <div className="py-12 text-center text-slate-500">
              <Loader2 className="w-6 h-6 animate-spin mx-auto mb-2 text-blue-500" />
              <span>Searching rooms...</span>
            </div>
          ) : allRooms.length === 0 ? (
            <div className="py-12 text-center text-slate-500 text-xs">
              No chat rooms found matching your search.
            </div>
          ) : (
            allRooms.map((r) => (
              <div
                key={r.id}
                className="flex items-center justify-between p-3.5 rounded-2xl bg-slate-950/60 border border-slate-800/80 hover:border-slate-700 transition-all"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-xl bg-slate-800 flex items-center justify-center text-slate-400">
                    {r.isPrivate ? (
                    <EyeOff className="w-4 h-4 text-violet-400" />
                  ) : r.isProtected ? (
                    <Lock className="w-4 h-4 text-amber-400" />
                  ) : (
                    <Hash className="w-4 h-4" />
                  )}
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-white leading-tight flex items-center gap-2">
                      {r.name}
                      {r.isProtected && (
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20 font-semibold">
                          Protected
                        </span>
                      )}
                    </h4>
                    <span className="text-[11px] text-slate-400">Created room</span>
                  </div>
                </div>

                <button
                  onClick={() => {
                    onSelectRoom(r.id);
                    onClose();
                  }}
                  className="px-3.5 py-2 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl flex items-center gap-1 shadow-md shadow-blue-600/20 transition-all"
                >
                  <span>Join</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
