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


def _ask(system: str, content: str, max_tokens: int = 2048) -> str:
    msg = anthropic.Anthropic().messages.create(
        model=MODEL, max_tokens=max_tokens, system=system,
        messages=[{"role": "user", "content": content}],
    )
    return msg.content[0].text.strip()


def extract_ideas(text: str, on_progress=None) -> str:
    """Хэсэг бүрээс гол санааг Монголоор товч тэмдэглэнэ (үгчилэн орчуулахгүй)."""
    parts = list(_chunks(text))
    notes = []
    for i, part in enumerate(parts, 1):
        notes.append(_ask(
            "Видеоны хадмалаас ГОЛ САНАА, баримт, жишээ, дүгнэлтийг Монгол хэлээр "
            "5-8 товч тэмдэглэл болгон гарга. Үгчилэн орчуулахгүй, өгүүлбэр хуулахгүй, "
            "зөвхөн санааг өөрийн үгээр бич.",
            part, 1024))
        if on_progress:
            on_progress(i, len(parts))
    return "\n".join(notes)


def write_original_script(notes: str, minutes: int = 10) -> str:
    """Тэмдэглэлээс шинэ бүтэцтэй, өөрийн өнгө аястай Монгол скрипт бичнэ."""
    return _ask(
        "Чи Монгол YouTube контент бүтээгч. Өгөгдсөн санааны тэмдэглэлээс "
        f"~{minutes} минутын ШИНЭ, ОРИГИНАЛ скрипт бич. Эх видеоны бүтэц, дараалал, "
        "өгүүлбэрийг дагахгүй: өөрийн hook, өөрийн бүтэц, Монгол хүнд ойр жишээ ашигла. "
        "Шинжлэх ухааны баталгаагүй зүйлийг баримт мэт бүү танилц, 'нэг үзэл бодол' "
        "гэж тодорхой хэл. Бүтэц: Hook, Үндсэн хэсгүүд, Дүгнэлт.",
        notes[:60000], 4096)


def translate_video(url: str, on_progress=None, minutes: int = 10) -> tuple[str, str]:
    """(гол санаанууд, шинэ оригинал скрипт) буцаана."""
    vid = extract_video_id(url)
    if not vid:
        raise ValueError("YouTube линк танигдсангүй")
    notes = extract_ideas(fetch_transcript(vid), on_progress)
    return notes, write_original_script(notes, minutes)
