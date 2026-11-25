"""Тушаалын мөрөөс: python cli.py <youtube-линк>"""
import sys

from translator import translate_video

if len(sys.argv) < 2:
    sys.exit("Хэрэглээ: python cli.py <youtube-линк>")
notes, script = translate_video(
    sys.argv[1], lambda i, n: print(f"орчуулж байна {i}/{n}", file=sys.stderr))
print("== ГОЛ САНАА ==\n" + notes + "\n\n== ШИНЭ СКРИПТ ==\n" + script)
