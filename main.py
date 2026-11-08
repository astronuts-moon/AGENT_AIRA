from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from groq import Groq
import os
import json
from dotenv import load_dotenv

load_dotenv()

app = FastAPI()

# 1. Groq Client
api_key = os.getenv("GROQ_API_KEY")
client = Groq(api_key=api_key)

class ChatRequest(BaseModel):
    message: str

# 2. உங்கள் Groq கணக்கில் உள்ள பிரதான Chat Models
CHAT_MODELS = [
    "openai/gpt-oss-120b",
    "openai/gpt-oss-20b",
    "qwen/qwen3.8-27b",
    "allam-2-7b"
]

@app.post("/chat")
async def chat_with_aira(request: ChatRequest):
    print(f"\n[உள்ளீடு] பயனர்: {request.message}")
    
    # பிரதான மாடல்களை வரிசையாகச் சோதிக்கும் லூப்
    for model_name in CHAT_MODELS:
        try:
            print(f"🔄 Groq [{model_name}] மூலம் முயல்கிறது...")
            
            completion = client.chat.completions.create(
                model=model_name,
                messages=[
                    {
                        "role": "system",
                        "content": "You are AIRA, a smart personal AI assistant. Always respond in valid JSON format like: {\"reply\": \"your response here\"}"
                    },
                    {
                        "role": "user",
                        "content": request.message
                    }
                ]
            )
            
            raw_text = completion.choices[0].message.content.strip()
            
            # Markdown சுற்றுகளை அகற்றுதல்
            if "```" in raw_text:
                raw_text = raw_text.replace("```json", "").replace("```", "").strip()
                
            try:
                output_data = json.loads(raw_text)
            except Exception:
                output_data = {"reply": raw_text}

            print(f"✅ [{model_name}] வெற்றி: {output_data}")
            return output_data

        except Exception as e:
            print(f"⚠️ [{model_name} பிழை]: {e}")
            continue

    return {"reply": "மன்னித்துக்கொள்ளுங்கள், Groq இணைப்பில் பிழை ஏற்பட்டுள்ளது."}