
import React, { useState, useCallback } from 'react';
import { extractTextFromImage } from './services/geminiService';
import { ImageUploader } from './components/ImageUploader';
import { ResultDisplay } from './components/ResultDisplay';
import { ScanningAnimation } from './components/ScanningAnimation';

const App: React.FC = () => {
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [extractedText, setExtractedText] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const handleImageChange = (file: File) => {
    if (file) {
      setImageFile(file);
      setImageUrl(URL.createObjectURL(file));
      setExtractedText(null);
      setError(null);
    }
  };

  const handleReset = useCallback(() => {
    setImageFile(null);
    if (imageUrl) {
      URL.revokeObjectURL(imageUrl);
    }
    setImageUrl(null);
    setExtractedText(null);
    setError(null);
    setIsLoading(false);
  }, [imageUrl]);

  const handleExtractText = useCallback(async () => {
    if (!imageFile) {
      setError('Please select an image first.');
      return;
    }

    setIsLoading(true);
    setError(null);
    setExtractedText(null);

    try {
      const text = await extractTextFromImage(imageFile);
      setExtractedText(text);
    } catch (err) {
      console.error(err);
      setError('Failed to extract text. The image might be unsupported or an API error occurred. Please try again.');
    } finally {
      setIsLoading(false);
    }
  }, [imageFile]);

  return (
    <div className="bg-gray-900 min-h-screen text-gray-100 flex flex-col items-center p-4 sm:p-6 lg:p-8 font-sans">
      <div className="w-full max-w-4xl mx-auto">
        <header className="text-center mb-8">
          <h1 className="text-4xl sm:text-5xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-teal-400 to-blue-500">
            Agentic OCR
          </h1>
          <p className="text-gray-400 mt-2 text-lg">
            Instantly extract text from your images with the power of AI.
          </p>
        </header>

        <main className="bg-gray-800 shadow-2xl rounded-2xl p-6 sm:p-8 w-full">
          {!imageUrl && (
            <ImageUploader onImageSelect={handleImageChange} />
          )}

          {imageUrl && (
            <div className="flex flex-col items-center">
              <div className="w-full max-w-lg mb-6 border-4 border-gray-700 rounded-xl overflow-hidden shadow-lg relative">
                <img src={imageUrl} alt="Selected preview" className="w-full h-auto object-contain" />
                <ScanningAnimation isVisible={isLoading} />
              </div>
              
              <div className="w-full">
                {isLoading ? (
                  <div className="flex flex-col items-center justify-center text-center">
                    <p className="text-teal-400 font-semibold text-lg">Scanning image for text...</p>
                    <p className="text-gray-400">AI is analyzing your image</p>
                  </div>
                ) : (
                  <>
                    {extractedText ? (
                      <ResultDisplay text={extractedText} onReset={handleReset} />
                    ) : (
                      <div className="flex flex-col sm:flex-row gap-4 justify-center">
                        <button
                          onClick={handleExtractText}
                          className="w-full sm:w-auto bg-teal-500 hover:bg-teal-600 text-white font-bold py-3 px-8 rounded-lg transition-transform transform hover:scale-105 focus:outline-none focus:ring-4 focus:ring-teal-500/50 shadow-lg"
                          disabled={isLoading}
                        >
                          Extract Text
                        </button>
                        <button
                          onClick={handleReset}
                          className="w-full sm:w-auto bg-gray-600 hover:bg-gray-700 text-white font-bold py-3 px-6 rounded-lg transition-colors focus:outline-none focus:ring-4 focus:ring-gray-600/50"
                          disabled={isLoading}
                        >
                          Choose Another Image
                        </button>
                      </div>
                    )}
                  </>
                )}
              </div>
            </div>
          )}

          {error && !isLoading && (
            <div className="mt-6 text-center text-red-400 bg-red-900/50 border border-red-500 p-4 rounded-lg">
              <p className="font-semibold">An Error Occurred</p>
              <p>{error}</p>
            </div>
          )}
        </main>

        <footer className="text-center mt-8 text-gray-500 text-sm">
          <p>Demo by Metrized</p>
        </footer>
      </div>
    </div>
  );
};

export default App;
