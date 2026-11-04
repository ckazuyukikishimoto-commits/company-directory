# icon-*.png から Windows 用の app.ico を作る（Pillow が必要）
from pathlib import Path
from PIL import Image
d = Path(__file__).parent / "images"
sizes = [16, 24, 32, 48, 64, 128, 256]
Image.open(d / "icon-256.png").save(d / "app.ico", sizes=[(s, s) for s in sizes])
