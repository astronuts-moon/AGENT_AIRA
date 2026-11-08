from pydantic import BaseModel
from typing import Optional, List, Dict, Any


class ActionStep(BaseModel):
    step: str
    element_id: Optional[str] = None
    text: Optional[str] = None
    direction: Optional[str] = None

    class Config:
        extra = "allow"  # Allow extra fields for dynamic steps


class DeviceAction(BaseModel):
    intent: str
    target_package: Optional[str] = None
    action_steps: List[Dict[str, Any]] = []


class ChatResponse(BaseModel):
    status: str
    ai_speech_reply: str
    device_action: Optional[DeviceAction] = None

    class Config:
        json_schema_extra = {
            "example": {
                "status": "success",
                "ai_speech_reply": "சரி ப்ரோ, யூடியூபில் பாடல் ஓபன் செய்கிறேன்.",
                "device_action": {
                    "intent": "open_app",
                    "target_package": "com.google.android.youtube",
                    "action_steps": [
                        {"step": "launch_app"},
                        {"step": "click", "element_id": "search_icon"},
                        {"step": "type_text", "text": "songs"}
                    ]
                }
            }
        }
