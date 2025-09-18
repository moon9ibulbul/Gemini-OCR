import React from 'react';

interface ScanningAnimationProps {
  isVisible: boolean;
}

export const ScanningAnimation: React.FC<ScanningAnimationProps> = ({ isVisible }) => {
  if (!isVisible) return null;

  return (
    <div className="absolute inset-0 pointer-events-none">
      {/* Scanning line that moves from top to bottom */}
      <div className="absolute w-full h-1 bg-gradient-to-r from-transparent via-teal-400 to-transparent opacity-80 animate-scan-line"></div>
      
      {/* Corner brackets */}
      <div className="absolute top-2 left-2 w-6 h-6 border-l-2 border-t-2 border-teal-400 animate-pulse"></div>
      <div className="absolute top-2 right-2 w-6 h-6 border-r-2 border-t-2 border-teal-400 animate-pulse"></div>
      <div className="absolute bottom-2 left-2 w-6 h-6 border-l-2 border-b-2 border-teal-400 animate-pulse"></div>
      <div className="absolute bottom-2 right-2 w-6 h-6 border-r-2 border-b-2 border-teal-400 animate-pulse"></div>
      
      {/* Scanning grid overlay */}
      <div className="absolute inset-0 opacity-20">
        <div className="w-full h-full bg-gradient-to-b from-transparent via-teal-400/10 to-transparent animate-scan-grid"></div>
      </div>
      
      {/* Pulsing center dot */}
      <div className="absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 w-3 h-3 bg-teal-400 rounded-full animate-ping"></div>
    </div>
  );
};
