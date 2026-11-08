from pydantic import BaseModel, Field
from typing import Optional


class ChatRequest(BaseModel):
    user_id: str = Field(..., description="Unique device identifier")
    voice_text: str = Field(..., description="Transcribed voice text from the user")
    current_screen: Optional[str] = Field("unknown", description="Current screen context")

    class Config:
        json_schema_extra = {
            "example": {
                "user_id": "unique_device_id_123",
                "voice_text": "Bro, YouTube-ல் பாட்டு போடு",
                "current_screen": "home_screen"
            }
        }
