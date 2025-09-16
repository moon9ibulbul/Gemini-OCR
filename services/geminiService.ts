
import { GoogleGenAI } from "@google/genai";

const API_KEY = process.env.API_KEY;

if (!API_KEY) {
  throw new Error("API_KEY environment variable is not set.");
}

const ai = new GoogleGenAI({ apiKey: API_KEY });

const fileToGenerativePart = (file: File): Promise<{
  inlineData: {
    data: string;
    mimeType: string;
  };
}> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result !== 'string') {
        return reject(new Error('Failed to read file as base64 string.'));
      }
      const base64Data = reader.result.split(',')[1];
      resolve({
        inlineData: {
          data: base64Data,
          mimeType: file.type,
        },
      });
    };
    reader.onerror = (error) => reject(error);
    reader.readAsDataURL(file);
  });
};


export const extractTextFromImage = async (imageFile: File): Promise<string> => {
  try {
    const imagePart = await fileToGenerativePart(imageFile);
    
    const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash',
        contents: [
            {
                parts: [
                    imagePart,
                    { text: "Extract all text from this image. Be precise and accurate, preserving line breaks where appropriate." }
                ]
            }
        ]
    });
    
    if (!response.text) {
        throw new Error("The API response did not contain any text.");
    }

    return response.text.trim();
  } catch (error) {
    console.error("Error calling Gemini API:", error);
    throw new Error("Failed to communicate with the Gemini API.");
  }
};

export const chatWithExtractedText = async (extractedText: string, userMessage: string): Promise<string> => {
  try {
    const response = await ai.models.generateContent({
        model: 'gemini-2.5-flash-lite',
        contents: [
            {
                parts: [
                    { 
                        text: `Here is the text that was extracted from an image using OCR:\n\n
                        ${extractedText}\n\n
                        You are a helpful assistant that can answer questions about the extracted text.
                        User question: ${userMessage}\n\n
                        Please provide a helpful response based on the extracted text.` 
                    }
                ]
            }
        ]
    });
    
    if (!response.text) {
        throw new Error("The API response did not contain any text.");
    }

    return response.text.trim();
  } catch (error) {
    console.error("Error calling Gemini API for chat:", error);
    throw new Error("Failed to communicate with the Gemini API for chat.");
  }
};