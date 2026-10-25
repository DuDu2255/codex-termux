# Launcher artwork

`launcher-source.png` is the image supplied by the repository owner:
https://file.garden/aqVNu8vFOoGzfTq2/icon.png

Run `python3 android/art/generate_launcher.py` with Pillow installed to regenerate
the committed resources. The launcher uses a black adaptive background and an
inset foreground to keep the supplied wordmark visible under circular masks.
Android 13 themed icons use the lettering's luminance as an alpha mask.
