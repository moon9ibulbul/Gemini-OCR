
import React, { useRef } from 'react';

interface ImageUploaderProps {
  onImageSelect: (file: File) => void;
}

const UploadIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="h-8 w-8 mr-3 text-teal-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
  </svg>
);

const CameraIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="h-8 w-8 mr-3 text-blue-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
  </svg>
);


export const ImageUploader: React.FC<ImageUploaderProps> = ({ onImageSelect }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const cameraInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files[0]) {
      onImageSelect(event.target.files[0]);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center p-8 border-2 border-dashed border-gray-600 rounded-xl text-center">
      <h2 className="text-2xl font-semibold mb-4 text-gray-200">Select an Image</h2>
      <p className="text-gray-400 mb-8 max-w-md">Upload a file from your device or use your camera to capture a new image for text extraction.</p>
      
      <div className="flex flex-col sm:flex-row gap-4 w-full justify-center max-w-lg">
        <input
          type="file"
          accept="image/*"
          ref={fileInputRef}
          onChange={handleFileChange}
          className="hidden"
        />
        <input
          type="file"
          accept="image/*"
          capture="environment"
          ref={cameraInputRef}
          onChange={handleFileChange}
          className="hidden"
        />
        
        <button
          onClick={() => fileInputRef.current?.click()}
          className="w-full flex items-center justify-center bg-gray-700 hover:bg-teal-800/50 border border-gray-600 hover:border-teal-500 text-white font-bold py-4 px-6 rounded-lg transition-all transform hover:-translate-y-1 duration-300 shadow-md"
        >
          <UploadIcon />
          Upload Image
        </button>

        <button
          onClick={() => cameraInputRef.current?.click()}
          className="w-full flex items-center justify-center bg-gray-700 hover:bg-blue-800/50 border border-gray-600 hover:border-blue-500 text-white font-bold py-4 px-6 rounded-lg transition-all transform hover:-translate-y-1 duration-300 shadow-md"
        >
          <CameraIcon />
          Use Camera
        </button>
      </div>
    </div>
  );
};
