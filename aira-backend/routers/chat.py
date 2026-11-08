import logging
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import JSONResponse

from models.request import ChatRequest
from models.response import ChatResponse, DeviceAction
from services.gemini_service import process_chat

router = APIRouter(prefix="/api/v1", tags=["AIRA Core"])
logger = logging.getLogger(__name__)


@router.get(
    "/health_check",
    summary="Health Check",
    description="Returns the current health status of the AIRA backend.",
)
async def health_check():
    return {"status": "ok", "version": "1.0.0", "service": "AIRA Backend", "model": "gemini-2.5-flash"}


@router.post(
    "/chat",
    response_model=ChatResponse,
    summary="Process Voice Command",
    description=(
        "Receives a transcribed voice command from the Android client, processes it through "
        "Gemini 2.5 Flash with function calling, and returns a speech reply plus optional device action."
    ),
)
async def chat(request: ChatRequest):
    logger.info(f"Chat request from user_id='{request.user_id}'")
    try:
        result = await process_chat(
            user_id=request.user_id,
            voice_text=request.voice_text,
            current_screen=request.current_screen or "unknown",
        )

        device_action = None
        if result.get("device_action"):
            da = result["device_action"]
            device_action = DeviceAction(
                intent=da.get("intent", ""),
                target_package=da.get("target_package"),
                action_steps=da.get("action_steps", []),
            )

        return ChatResponse(
            status=result["status"],
            ai_speech_reply=result["ai_speech_reply"],
            device_action=device_action,
        )

    except Exception as e:
        logger.error(f"Unhandled error in /chat: {e}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"AIRA processing error: {str(e)}",
        )
