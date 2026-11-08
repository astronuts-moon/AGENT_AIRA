import json
import logging
from typing import Any, Dict, Optional
import httpx

logger = logging.getLogger(__name__)

# In-memory store for this session
_memory_store: Dict[str, str] = {}

# Known app packages map
APP_PACKAGES = {
    "youtube": "com.google.android.youtube",
    "whatsapp": "com.whatsapp",
    "instagram": "com.instagram.android",
    "chrome": "com.android.chrome",
    "gmail": "com.google.android.gm",
    "maps": "com.google.android.apps.maps",
    "spotify": "com.spotify.music",
    "camera": "com.android.camera2",
    "settings": "com.android.settings",
    "phone": "com.android.dialer",
    "messages": "com.google.android.apps.messaging",
    "calculator": "com.android.calculator2",
    "clock": "com.android.deskclock",
    "contacts": "com.android.contacts",
    "gallery": "com.google.android.apps.photos",
    "playstore": "com.android.vending",
    "facebook": "com.facebook.katana",
    "twitter": "com.twitter.android",
    "telegram": "org.telegram.messenger",
    "zoom": "us.zoom.videomeetings",
}


def execute_device_action(app_package: str, action_type: str, parameters: str) -> Dict[str, Any]:
    """
    Assembles the device_action JSON payload the Android client will execute.
    Parameters is a JSON string with extra parameters.
    """
    params: Dict[str, Any] = {}
    if parameters and parameters.strip():
        try:
            params = json.loads(parameters)
        except json.JSONDecodeError:
            params = {"raw": parameters}

    # Resolve friendly names to package names
    resolved_package = APP_PACKAGES.get(app_package.lower(), app_package)
    action_steps: list = []

    if action_type == "open_app":
        action_steps = [{"step": "launch_app"}]

    elif action_type == "open_and_search":
        query = params.get("query", "")
        action_steps = [
            {"step": "launch_app"},
            {"step": "click", "element_id": "search_icon"},
            {"step": "type_text", "text": query},
            {"step": "click", "element_id": "search_button"},
        ]

    elif action_type == "open_and_call":
        contact = params.get("contact", "")
        action_steps = [
            {"step": "launch_app"},
            {"step": "click", "element_id": "dialpad"},
            {"step": "type_text", "text": contact},
            {"step": "click", "element_id": "call_button"},
        ]

    elif action_type == "send_message":
        contact = params.get("contact", "")
        message = params.get("message", "")
        action_steps = [
            {"step": "launch_app"},
            {"step": "click", "element_id": "new_message_fab"},
            {"step": "type_text", "text": contact},
            {"step": "click", "element_id": "contact_suggestion_0"},
            {"step": "click", "element_id": "message_input"},
            {"step": "type_text", "text": message},
            {"step": "click", "element_id": "send_button"},
        ]

    elif action_type == "navigate_back":
        action_steps = [{"step": "navigate_back"}]

    elif action_type == "navigate_home":
        action_steps = [{"step": "navigate_home"}]

    elif action_type == "scroll":
        direction = params.get("direction", "down")
        action_steps = [{"step": "scroll", "direction": direction}]

    elif action_type == "take_screenshot":
        action_steps = [{"step": "take_screenshot"}]

    elif action_type == "adjust_volume":
        direction = params.get("direction", "up")
        steps = params.get("steps", 1)
        action_steps = [{"step": "adjust_volume", "direction": direction, "steps": steps}]

    elif action_type == "toggle_wifi":
        action_steps = [{"step": "toggle_wifi"}]

    elif action_type == "toggle_bluetooth":
        action_steps = [{"step": "toggle_bluetooth"}]

    elif action_type == "toggle_flashlight":
        action_steps = [{"step": "toggle_flashlight"}]

    else:
        action_steps = [{"step": action_type, **params}]

    logger.info(f"Device action assembled: {action_type} on {resolved_package}")
    return {
        "device_action_ready": True,
        "intent": action_type,
        "target_package": resolved_package,
        "action_steps": action_steps,
    }


def manage_secure_memory(action: str, topic: str, content: Optional[str] = None) -> Dict[str, Any]:
    """
    Save, read, delete, or list entries in secure memory.
    """
    action = action.lower()
    if action == "save":
        _memory_store[topic] = content or ""
        logger.info(f"Memory saved: topic='{topic}'")
        return {"status": "saved", "topic": topic}

    elif action == "read":
        value = _memory_store.get(topic)
        if value is not None:
            return {"status": "found", "topic": topic, "content": value}
        return {"status": "not_found", "topic": topic, "content": None}

    elif action == "delete":
        removed = _memory_store.pop(topic, None)
        return {"status": "deleted" if removed is not None else "not_found", "topic": topic}

    elif action == "list":
        return {"status": "ok", "topics": list(_memory_store.keys()), "count": len(_memory_store)}

    return {"status": "error", "message": f"Unknown action: {action}"}


async def search_web(query: str) -> Dict[str, Any]:
    """
    Search the web using DuckDuckGo Instant Answer API (no key required).
    """
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(
                "https://api.duckduckgo.com/",
                params={"q": query, "format": "json", "no_html": "1", "skip_disambig": "1"},
            )
            data = response.json()

            abstract = data.get("AbstractText", "")
            answer = data.get("Answer", "")
            related = [
                r.get("Text", "")
                for r in data.get("RelatedTopics", [])[:3]
                if isinstance(r, dict) and r.get("Text")
            ]

            if abstract:
                return {"result": abstract, "source": data.get("AbstractURL", "")}
            if answer:
                return {"result": answer}
            if related:
                return {"result": " | ".join(related)}
            return {"result": f"Could not find a direct answer for: {query}. Please try a more specific query."}

    except httpx.TimeoutException:
        return {"result": "Web search timed out. Please try again."}
    except Exception as e:
        logger.error(f"Web search error: {e}")
        return {"result": f"Web search failed: {str(e)}"}
