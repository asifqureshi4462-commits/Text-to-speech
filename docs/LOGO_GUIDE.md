# Logo guide - how your logo is used and how to replace it safely

Your logo is already integrated (it came from the file you uploaded). Nothing was redrawn: the project only contains
crops and resized copies of **your** picture. Follow this guide only if you want to swap in a new logo later.

## The easy, safe way (recommended): one command

1. Prepare **one square image** of your logo: PNG, WEBP or JPG, **at least 1024 x 1024 px** (bigger is fine).
   Best result: the full logo tile on a plain white background, like the original, with the icon mark (play button + microphone + waveform) in the upper-middle part.
2. Put it anywhere, for example `my_logo.png` in the project root, then run (from the project root):
   ```bash
   pip install pillow numpy
   python3 tools/make_logo_assets.py my_logo.png
   ```
3. The script writes every file listed below, with the exact names and sizes the project expects. It prints a background colour.
   If your logo's background differs from `#060B2B`, open `app/src/main/res/values/colors.xml` and change the two lines
   `ic_launcher_background` and `splash_bg` to that colour.
4. Check the project is still consistent: `python3 tools/check_project.py`
5. Rebuild: push to GitHub (Actions builds the APK) or run **Build > Rebuild Project** in Android Studio.
   If the launcher still shows the old icon, uninstall the old app from the phone and install again.

If the mark in your new logo is in a different position, pass its area as fractions of the picture:
`python3 tools/make_logo_assets.py my_logo.png --mark-box 0.15,0.15,0.85,0.60`
(`x0,y0,x1,y1`, each between 0 and 1). If a dark fringe remains around the mark, add `--bg-threshold 50`.

## Files the logo lives in (exact paths, formats and sizes)

| File (relative to the project root) | Format / size | Used for |
|---|---|---|
| `app/src/main/res/drawable-nodpi/brand_logo.png` | PNG, **768 x 768**, transparent corners | Splash screen, Login, Sign up, Forgot password, Verify email, Onboarding, Home header, Settings header |
| `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png` | PNG, **432 x 432**, transparent background, mark kept inside the centre ~290 px | App icon (adaptive icon foreground) and the Android system splash icon |
| `app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png` | PNG, **432 x 432**, white shape on transparent | Android 13+ "themed icons" |
| `app/src/main/res/drawable-nodpi/brand_mark.png` | PNG, 512 px wide, transparent | The mark alone (available for your own use) |
| `app/src/main/res/values/colors.xml` -> `ic_launcher_background` | colour value | App icon background behind the foreground |
| `app/src/main/res/values/colors.xml` -> `splash_bg` | colour value | Splash screen background |
| `backend/public/logo.png` | PNG, 256 x 256 | Admin dashboard header and the email-verification web page |
| `backend/public/favicon.png` | PNG, 64 x 64 | Browser tab icon of the admin page |
| `docs/play_store_icon_512.png` | PNG, 512 x 512 | Upload to the Google Play listing (not used by the app) |

Wiring (you normally never edit these): `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
point at the foreground, monochrome and background; `app/src/main/res/values/themes.xml` (`Theme.App.Starting`) points the
system splash at `ic_launcher_foreground`; screens reference `@drawable/brand_logo`.

## Do I need separate images for different Android icon sizes?

**No.** The app's minimum Android version is 8.0 (API 26), which always uses *adaptive icons*. Android scales the single
`ic_launcher_foreground.png` for every screen density, so you do **not** need `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi` folders.
Because the files are in `drawable-nodpi`, Android also will not resize them behind your back.

## Replacing files by hand (only if you do not use the script)

* Keep the **same file names** and **same folders** as in the table; do not rename them or the build will fail with "resource not found".
* Use PNG with transparency for the files that say transparent. Do not use JPG or WEBP for these (no transparency).
* Keep the foreground mark inside the centre ~66% of the 432 x 432 canvas, otherwise round or squircle icon masks will cut it.
* Do not add the same name with a different extension (for example `brand_logo.webp` next to `brand_logo.png`); Android treats that as a duplicate and the build fails.
* Run `python3 tools/check_project.py` afterwards; it reports any missing logo resource.

## What to check after replacing

* Install the new APK: launcher icon, splash screen, Login screen, Home header and Settings header show your logo.
* Round icons: long-press the app icon / switch to a round-icon launcher and make sure nothing is clipped.
* Dark and light themes: the logo tile is opaque, so it looks the same in both.

## Rights

You are responsible for owning the rights to the logo you use. The project does not include any other logo or third-party artwork.
