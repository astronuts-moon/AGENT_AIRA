import json
import logging
from typing import Any, Dict, Optional
import asyncio

from google import genai
from google.genai import types

from config import settings
from services.tools import execute_device_action, manage_secure_memory, search_web

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """You are AIRA (Artificial Intelligence Responsive Assistant), an advanced AI device assistant and cybersecurity expert. You intelligently control the user's Android smartphone based on their voice commands.

Personality:
- Friendly, smart, and concise — like a knowledgeable bro/companion.
- When the user speaks in Tamil, reply naturally in Tamil (or Tanglish).
- When they speak in English, reply in English.
- Keep voice replies short (1-2 sentences max) — they will be spoken aloud via TTS.

Capabilities:
- Open any app and perform actions inside it.
- Send messages, make calls, search the internet.
- Remember user preferences and notes.
- Answer general knowledge questions.

Rules:
- ALWAYS use execute_device_action tool when the user asks to open an app, click something, type something, or control the phone.
- ALWAYS use manage_secure_memory when the user asks to remember something.
- ALWAYS use search_web when you need current or factual information.
- Be proactive — infer intent from context (current_screen).
- Never expose internal tool results verbatim in your speech reply."""

# ── Tool declarations ──────────────────────────────────────────────────────────
AIRA_TOOLS = types.Tool(
    function_declarations=[
        types.FunctionDeclaration(
            name="execute_device_action",
            description=(
                "Control the Android device: open apps, click UI elements, type text, "
                "make calls, send messages, adjust volume, toggle settings, scroll."
            ),
            parameters=types.Schema(
                type="OBJECT",
                properties={
                    "app_package": types.Schema(
                        type="STRING",
                        description=(
                            "App package name or friendly name. E.g. 'com.google.android.youtube', "
                            "'whatsapp', 'instagram', 'chrome', 'settings', 'phone', 'camera'."
                        ),
                    ),
                    "action_type": types.Schema(
                        type="STRING",
                        description=(
                            "Action to perform. One of: open_app, open_and_search, send_message, "
                            "open_and_call, scroll, navigate_back, navigate_home, take_screenshot, "
                            "adjust_volume, toggle_wifi, toggle_bluetooth, toggle_flashlight."
                        ),
                    ),
                    "parameters": types.Schema(
                        type="STRING",
                        description=(
                            "JSON string with extra parameters. Examples: "
                            '{"query": "Tamil songs"} for open_and_search, '
                            '{"contact": "Amma", "message": "Hello"} for send_message, '
                            '{"direction": "up", "steps": 3} for adjust_volume.'
                        ),
                    ),
                },
                required=["app_package", "action_type", "parameters"],
            ),
        ),
        types.FunctionDeclaration(
            name="manage_secure_memory",
            description=(
                "Save, read, delete, or list notes and user preferences in secure memory. "
                "Use this to remember things the user asks you to remember."
            ),
            parameters=types.Schema(
                type="OBJECT",
                properties={
                    "action": types.Schema(
                        type="STRING",
                        description="One of: save, read, delete, list.",
                    ),
                    "topic": types.Schema(
                        type="STRING",
                        description="Key/topic for the memory entry (e.g. 'wifi_password', 'meeting_note').",
                    ),
                    "content": types.Schema(
                        type="STRING",
                        description="Content to save. Only required for the 'save' action.",
                    ),
                },
                required=["action", "topic"],
            ),
        ),
        types.FunctionDeclaration(
            name="search_web",
            description=(
                "Search the internet for current information, news, weather, sports scores, or facts."
            ),
            parameters=types.Schema(
                type="OBJECT",
                properties={
                    "query": types.Schema(
                        type="STRING",
                        description="The search query string.",
                    ),
                },
                required=["query"],
            ),
        ),
    ]
)


def _build_device_action(tool_result: Dict) -> Optional[Dict]:
    """Extract device action from tool result if present."""
    if tool_result.get("device_action_ready"):
        return {
            "intent": tool_result.get("intent", ""),
            "target_package": tool_result.get("target_package"),
            "action_steps": tool_result.get("action_steps", []),
        }
    return None


def _process_chat_sync(
    user_id: str, voice_text: str, current_screen: str
) -> Dict[str, Any]:
    """Synchronous Gemini agentic loop with function calling."""
    client = genai.Client(api_key=settings.GEMINI_API_KEY)

    config = types.GenerateContentConfig(
        system_instruction=SYSTEM_PROMPT,
        tools=[AIRA_TOOLS],
        temperature=0.7,
        max_output_tokens=1024,
    )

    device_action: Optional[Dict] = None
    speech_reply = ""

    # Build the initial user message with screen context
    user_message = f"[Current screen: {current_screen}] {voice_text}"
    logger.info(f"Processing: user_id={user_id}, text='{voice_text}', screen='{current_screen}'")

    # Start chat session
    chat = client.chats.create(model="gemini-2.5-flash", config=config)
    response = chat.send_message(user_message)

    # Agentic loop — Gemini may call multiple tools before final answer
    MAX_TOOL_ROUNDS = 6
    for iteration in range(MAX_TOOL_ROUNDS):
        fn_call = None
        text_parts = []

        for part in response.candidates[0].content.parts:
            if hasattr(part, "function_call") and part.function_call and part.function_call.name:
                fn_call = part.function_call
                break
            if hasattr(part, "text") and part.text:
                text_parts.append(part.text)

        if fn_call is None:
            # Final text response reached
            speech_reply = " ".join(text_parts).strip()
            logger.info(f"Final reply after {iteration} tool calls: '{speech_reply[:80]}...'")
            break

        # Execute the called tool
        fn_name = fn_call.name
        fn_args = dict(fn_call.args) if fn_call.args else {}
        tool_result: Dict[str, Any] = {}
        logger.info(f"Tool called: {fn_name}({fn_args})")

        if fn_name == "execute_device_action":
            tool_result = execute_device_action(
                fn_args.get("app_package", ""),
                fn_args.get("action_type", ""),
                fn_args.get("parameters", "{}"),
            )
            action_data = _build_device_action(tool_result)
            if action_data:
                device_action = action_data

        elif fn_name == "manage_secure_memory":
            tool_result = manage_secure_memory(
                fn_args.get("action", "read"),
                fn_args.get("topic", ""),
                fn_args.get("content"),
            )

        elif fn_name == "search_web":
            # search_web is async but we are in sync context — use asyncio
            tool_result = asyncio.get_event_loop().run_until_complete(
                search_web(fn_args.get("query", ""))
            )

        # Feed result back to Gemini
        response = chat.send_message(
            types.Part.from_function_response(
                name=fn_name,
                response=tool_result,
            )
        )

    if not speech_reply:
        speech_reply = "Done! Action completed successfully."

    return {
        "status": "success",
        "ai_speech_reply": speech_reply,
        "device_action": device_action,
    }


async def process_chat(
    user_id: str, voice_text: str, current_screen: str
) -> Dict[str, Any]:
    """Async wrapper around the synchronous Gemini processing."""
    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(
        None,
        _process_chat_sync,
        user_id,
        voice_text,
        current_screen,
    )
    return result
