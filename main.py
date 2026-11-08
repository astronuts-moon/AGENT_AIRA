from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from groq import Groq
import os
import json
from dotenv import load_dotenv

load_dotenv()

app = FastAPI()

@app.get("/")
def read_root():
    return {"status": "AIRA Backend is Running!", "version": "1.0.0"}

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

# 1. Root Endpoint (பிரவுசர் சோதனைகளுக்காக)
@app.get("/")
async def root():
    return {"status": "AIRA Backend is running successfully!"}

# 2. Health Endpoint
@app.get("/health")
async def health():
    return {"status": "healthy"}

import os
from fastapi import FastAPI, HTTPException, Header, Depends
from dotenv import load_dotenv

load_dotenv()

AIRA_SECRET_KEY = os.getenv("AIRA_SECRET_KEY", "aira_secure_token_2026_x89q")

@app.post("/chat")
async def chat(request: ChatRequest, x_aira_secret_key: str = Header(None)):
    # Secret Key இல்லையென்றால் அல்லது தவறாக இருந்தால் 401 Unauthorized எரர் வரும்
    if x_aira_secret_key != AIRA_SECRET_KEY:
        raise HTTPException(status_code=401, detail="Unauthorized Access to AIRA Backend")
    
    # ... உங்கள் Groq API அழைப்பு கோடுகள் தொடரும் ...
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