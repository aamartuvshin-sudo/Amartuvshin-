"""YouTube видеоны хадмал/транскриптийг татаж, Монгол хэл рүү орчуулна."""
import os
import re

import anthropic
from youtube_transcript_api import YouTubeTranscriptApi

MODEL = os.environ.get("TRANSLATE_MODEL", "claude-sonnet-5-5")
CHUNK_CHARS = 6000

_ID_RE = re.compile(r"(?:youtu\.be/|v=|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})")


def extract_video_id(text: str) -> str | None:
    m = _ID_RE.search(text)
    if m:
        return m.group(1)
    text = text.strip()
    return text if re.fullmatch(r"[A-Za-z0-9_-]{11}", text) else None


def fetch_transcript(video_id: str) -> str:
    api = YouTubeTranscriptApi()
    tlist = api.list(video_id)
    try:
        t = tlist.find_manually_created_transcript(
            [t.language_code for t in tlist]
        )
    except Exception:
        t = next(iter(tlist))
    fetched = t.fetch()
    return " ".join(s.text.replace("\n", " ") for s in fetched)


def _chunks(text: str, size: int = CHUNK_CHARS):
    words, cur, n = text.split(), [], 0
    for w in words:
        if n + len(w) + 1 > size and cur:
            yield " ".join(cur)
            cur, n = [], 0
        cur.append(w)
        n += len(w) + 1
    if cur:
        yield " ".join(cur)


def translate(text: str, on_progress=None) -> str:
    client = anthropic.Anthropic()
    parts = list(_chunks(text))
    out = []
    for i, part in enumerate(parts, 1):
        msg = client.messages.create(
            model=MODEL,
            max_tokens=4096,
            system=(
                "Чи мэргэжлийн орчуулагч. Өгөгдсөн видеоны хадмалыг эх хэлнээс нь "
                "Монгол хэл (кирилл) рүү утгыг нь алдагдуулахгүй, ойлгомжтой, "
                "байгалийн өнгө аясаар орчуул. Өгүүлбэр, догол мөрөнд хуваа. "
                "Зөвхөн орчуулгыг гарга, тайлбар нэмэхгүй."
            ),
            messages=[{"role": "user", "content": part}],
        )
        out.append(msg.content[0].text.strip())
        if on_progress:
            on_progress(i, len(parts))
    return "\n\n".join(out)


def summarize(mn_text: str) -> str:
    client = anthropic.Anthropic()
    msg = client.messages.create(
        model=MODEL,
        max_tokens=1024,
        system="Доорх орчуулгыг Монголоор 8-10 мөр товч хураангуйл, гол санаануудыг тусд нь тэмдэглэ.",
        messages=[{"role": "user", "content": mn_text[:60000]}],
    )
    return msg.content[0].text.strip()


def translate_video(url: str, on_progress=None) -> tuple[str, str]:
    vid = extract_video_id(url)
    if not vid:
        raise ValueError("YouTube линк танигдсангүй")
    mn = translate(fetch_transcript(vid), on_progress)
    return summarize(mn), mn
