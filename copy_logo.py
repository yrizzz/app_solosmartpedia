#!/usr/bin/env python3
"""
Jalankan script ini dari folder app_solosmartpedia setelah copy logo PNG:
  python3 copy_logo.py logo.png

Script akan resize otomatis ke semua ukuran mipmap dan drawable Android.
Butuh: pip install Pillow
"""

import sys
import os
from PIL import Image

SIZES = {
    "app/src/main/res/mipmap-mdpi":    48,
    "app/src/main/res/mipmap-hdpi":    72,
    "app/src/main/res/mipmap-xhdpi":   96,
    "app/src/main/res/mipmap-xxhdpi":  144,
    "app/src/main/res/mipmap-xxxhdpi": 192,
}

DRAWABLE_SIZE = 512  # for ic_logo.png in drawable

def resize_and_save(src_path):
    img = Image.open(src_path).convert("RGBA")

    # Save ic_logo.png to drawable (used in splash/welcome screens)
    drawable_dir = "app/src/main/res/drawable"
    os.makedirs(drawable_dir, exist_ok=True)
    out = img.resize((DRAWABLE_SIZE, DRAWABLE_SIZE), Image.LANCZOS)
    out.save(f"{drawable_dir}/ic_logo.png", "PNG")
    print(f"  Saved drawable/ic_logo.png ({DRAWABLE_SIZE}x{DRAWABLE_SIZE})")

    # Save ic_launcher.png to each mipmap folder
    for folder, size in SIZES.items():
        os.makedirs(folder, exist_ok=True)
        out = img.resize((size, size), Image.LANCZOS)
        out.save(f"{folder}/ic_launcher.png", "PNG")
        out.save(f"{folder}/ic_launcher_round.png", "PNG")
        print(f"  Saved {folder}/ic_launcher.png ({size}x{size})")

    print("\nSelesai! Sekarang di AndroidManifest.xml ganti:")
    print('  android:icon="@mipmap/ic_launcher"  <- sudah benar')
    print("\nDan di semua layout XML ganti:")
    print('  android:src="@drawable/ic_logo"     <- sudah benar')

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 copy_logo.py <path_to_logo.png>")
        sys.exit(1)
    src = sys.argv[1]
    if not os.path.exists(src):
        print(f"File tidak ditemukan: {src}")
        sys.exit(1)
    print(f"Memproses logo: {src}")
    resize_and_save(src)
