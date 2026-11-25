"""Тушаалын мөрөөс: python cli.py <youtube-линк>"""
import sys

from translator import translate_video

if len(sys.argv) < 2:
    sys.exit("Хэрэглээ: python cli.py <youtube-линк>")
summary, full = translate_video(
    sys.argv[1], lambda i, n: print(f"орчуулж байна {i}/{n}", file=sys.stderr))
print("== ХУРААНГУЙ ==\n" + summary + "\n\n== БҮТЭН ОРЧУУЛГА ==\n" + full)
