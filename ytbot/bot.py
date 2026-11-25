"""Telegram бот: YouTube линк илгээхэд Монгол орчуулга буцаана."""
import asyncio
import io
import os

from telegram import Update
from telegram.ext import (Application, CommandHandler, ContextTypes,
                          MessageHandler, filters)

from translator import extract_video_id, translate_video

START = ("Сайн уу! YouTube линк илгээвэл би хадмалыг нь Монгол хэл рүү "
         "орчуулж, хураангуйг нь болон бүтэн орчуулгыг .txt файлаар илгээнэ.")


async def start(update: Update, ctx: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(START)


async def handle(update: Update, ctx: ContextTypes.DEFAULT_TYPE):
    text = update.message.text or ""
    vid = extract_video_id(text)
    if not vid:
        await update.message.reply_text("YouTube линк илгээнэ үү.")
        return
    status = await update.message.reply_text("⏳ Хадмал татаж байна...")
    try:
        summary, full = await asyncio.to_thread(translate_video, text)
    except Exception as e:
        await status.edit_text(f"❌ Алдаа: {e}\n(Хадмалгүй видео байж болно)")
        return
    await status.delete()
    await update.message.reply_text(f"📝 Хураангуй\n\n{summary}"[:4000])
    f = io.BytesIO(full.encode("utf-8"))
    f.name = f"{vid}_mn.txt"
    await update.message.reply_document(f, caption="Бүтэн орчуулга")


def main():
    app = Application.builder().token(os.environ["TELEGRAM_BOT_TOKEN"]).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle))
    app.run_polling()


if __name__ == "__main__":
    main()
