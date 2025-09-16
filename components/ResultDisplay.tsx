
import React, { useState, useCallback } from 'react';
import { chatWithExtractedText } from '../services/geminiService';

interface ResultDisplayProps {
  text: string;
  onReset: () => void;
}

const CopyIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
  </svg>
);

const CheckIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
  </svg>
);


export const ResultDisplay: React.FC<ResultDisplayProps> = ({ text, onReset }) => {
  const [copied, setCopied] = useState(false);
  const [chatMessage, setChatMessage] = useState('');
  const [chatResponse, setChatResponse] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [text]);

  const handleChatSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatMessage.trim()) return;

    setIsLoading(true);
    setChatResponse('');
    
    try {
      const response = await chatWithExtractedText(text, chatMessage);
      setChatResponse(response);
    } catch (error) {
      console.error('Error getting chat response:', error);
      setChatResponse('Sorry, there was an error processing your request. Please try again.');
    } finally {
      setIsLoading(false);
    }
    
    setChatMessage('');
  };

  return (
    <div className="w-full flex flex-col items-center animate-fade-in">
      <h2 className="text-2xl font-bold mb-4 text-center text-teal-300">Extracted Text</h2>
      <div className="w-full bg-gray-900 rounded-lg p-6 border border-gray-700 max-h-96 overflow-y-auto mb-6 shadow-inner">
        <pre className="whitespace-pre-wrap text-gray-200 font-mono text-sm sm:text-base">{text}</pre>
      </div>
      
      {/* Chatbot Section */}
      <div className="w-full bg-gray-800 rounded-lg p-6 border border-gray-600 mb-6">
        <h3 className="text-lg font-semibold mb-4 text-teal-300">Chat with AI</h3>
        <form onSubmit={handleChatSubmit} className="flex gap-2 mb-4">
          <input
            type="text"
            value={chatMessage}
            onChange={(e) => setChatMessage(e.target.value)}
            placeholder="Summarize the extracted text for me..."
            disabled={isLoading}
            className="flex-1 bg-gray-700 text-white px-4 py-2 rounded-lg border border-gray-600 focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-transparent disabled:opacity-50 disabled:cursor-not-allowed"
          />
          <button
            type="submit"
            disabled={isLoading || !chatMessage.trim()}
            className="bg-teal-600 hover:bg-teal-700 disabled:bg-gray-600 disabled:cursor-not-allowed text-white font-bold py-2 px-4 rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-teal-500"
          >
            {isLoading ? 'Sending...' : 'Send'}
          </button>
        </form>
        
        {/* Chat Response */}
        {chatResponse && (
          <div className="bg-gray-700 rounded-lg p-4 border border-gray-600">
            <h4 className="text-sm font-semibold text-teal-300 mb-2">AI Response:</h4>
            <div className="text-gray-200 whitespace-pre-wrap">{chatResponse}</div>
          </div>
        )}
        
        {/* Loading State */}
        {isLoading && (
          <div className="bg-gray-700 rounded-lg p-4 border border-gray-600">
            <div className="flex items-center gap-2 text-teal-300">
              <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-teal-300"></div>
              <span>AI is thinking...</span>
            </div>
          </div>
        )}
      </div>

      <div className="flex flex-col sm:flex-row gap-4 justify-center w-full">
        <button
          onClick={handleCopy}
          className={`w-full sm:w-auto flex items-center justify-center font-bold py-3 px-6 rounded-lg transition-colors focus:outline-none focus:ring-4 shadow-lg ${
            copied
              ? 'bg-green-600 text-white focus:ring-green-500/50'
              : 'bg-blue-600 hover:bg-blue-700 text-white focus:ring-blue-600/50'
          }`}
        >
          {copied ? <CheckIcon /> : <CopyIcon />}
          {copied ? 'Copied!' : 'Copy to Clipboard'}
        </button>
        <button
          onClick={onReset}
          className="w-full sm:w-auto bg-gray-600 hover:bg-gray-700 text-white font-bold py-3 px-6 rounded-lg transition-colors focus:outline-none focus:ring-4 focus:ring-gray-600/50"
        >
          Start Over
        </button>
      </div>
    </div>
  );
};
