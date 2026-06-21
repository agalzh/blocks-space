"""Phase 0 smoke test: confirm Gemini key works end-to-end."""
import os
import sys
from pathlib import Path

from dotenv import load_dotenv
import google.generativeai as genai

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

key = os.getenv("GEMINI_API_KEY")
if not key:
    sys.exit("GEMINI_API_KEY missing from .env")

genai.configure(api_key=key)
model = genai.GenerativeModel("gemini-2.5-flash-lite")
resp = model.generate_content(
    "Reply with exactly the text: STACK_UNKNOWN_OK"
)
print("Gemini reply:", resp.text.strip())
