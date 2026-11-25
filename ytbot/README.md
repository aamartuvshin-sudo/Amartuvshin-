# YouTube → Монгол орчуулагч бот

YouTube линкийн хадмалыг (ямар ч хэлнээс) Монгол хэл рүү орчуулна.

## Ажиллуулах
```bash
pip install -r requirements.txt
export ANTHROPIC_API_KEY=...        # орчуулга хийх Claude түлхүүр
python cli.py https://youtu.be/50fWbSSb6w0        # тушаалын мөрөөр

export TELEGRAM_BOT_TOKEN=...       # @BotFather-аас авна
python bot.py                       # Telegram бот
```
Бот: линк илгээх → хураангуй + бүтэн орчуулга (.txt) буцаана.

Анхаарах: видео хадмалтай (өөрөө эсвэл автомат) байх ёстой. Түлхүүрүүдийг кодонд бүү бич.
