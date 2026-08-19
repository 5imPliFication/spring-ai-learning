import React from 'react';

interface AvatarProps {
  name?: string;
  src?: string | null;
  isAi?: boolean;
  size?: 'sm' | 'md' | 'lg';
}

const colorPairs = [
  'from-blue-600 to-indigo-600',
  'from-emerald-600 to-teal-600',
  'from-purple-600 to-pink-600',
  'from-amber-600 to-orange-600',
  'from-cyan-600 to-blue-600',
];

function getColorClass(name: string) {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  const index = Math.abs(hash) % colorPairs.length;
  return colorPairs[index];
}

export const Avatar: React.FC<AvatarProps> = ({ name = '?', src, isAi = false, size = 'md' }) => {
  const sizeClasses = {
    sm: 'w-7 h-7 text-xs',
    md: 'w-9 h-9 text-sm',
    lg: 'w-11 h-11 text-base',
  };

  const safeName = (name || '?').trim();

  if (isAi) {
    return (
      <div
        className={`${sizeClasses[size]} rounded-full bg-gradient-to-tr from-emerald-500 to-teal-400 text-slate-950 font-bold flex items-center justify-center shadow-lg shadow-emerald-950/50 ring-2 ring-emerald-400/30`}
      >
        ✨
      </div>
    );
  }

  if (src) {
    return (
      <img
        src={src}
        alt={`${safeName}'s avatar`}
        className={`${sizeClasses[size]} rounded-full object-cover bg-slate-800`}
      />
    );
  }

  const initial = safeName.charAt(0).toUpperCase() || '?';
  const gradient = getColorClass(safeName);

  return (
    <div
      className={`${sizeClasses[size]} rounded-full bg-gradient-to-tr ${gradient} text-white font-semibold flex items-center justify-center shadow-md select-none`}
    >
      {initial}
    </div>
  );
};
