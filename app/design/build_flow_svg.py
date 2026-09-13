"""
Generates design/pantry-tesco-flow.svg -- the deep-link Tesco basket flow.

Plain SVG on purpose: rects, paths and text with id/data-name on every group, so
the layer tree is navigable in a viewer (Figpea) and converts cleanly if the file
is ever imported into Figma. No <style> blocks, no foreignObject, no CSS classes.

Palette is lifted from app/src/main/java/com/pantry/app/ui/theme/Theme.kt so the
mockup and the built app cannot drift apart.
"""

FONT = "Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif"

C = {
    "canvas":       "#E8E4DA",
    "parchment":    "#FBF8F3",
    "surface":      "#FFFFFF",
    "outline":      "#DDD8CE",
    "primary":      "#2F5D3A",
    "primaryLight": "#5B8C63",
    "primaryCont":  "#CDE6D2",
    "onPrimCont":   "#0C2214",
    "secondary":    "#B4562F",
    "secondCont":   "#FFDBCD",
    "surfaceVar":   "#E0E5DC",
    "onSurfaceVar": "#444842",
    "onSurface":    "#1B1C19",
    "muted":        "#8A8F86",
    "success":      "#2E7D32",
    "error":        "#B3261E",
    "webChrome":    "#F1F1F1",
    "webLine":      "#E4E4E4",
    "webBlue":      "#1B5E9E",
    "scrim":        "#1B1C19",
}

FW, FH = 360, 760
COLS = [80, 520, 960, 1400]
ROWS = [150, 1100]
CANVAS_W = COLS[-1] + FW + 80
CANVAS_H = ROWS[-1] + FH + 200

out = []


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def att(s):
    """Attribute values also need quotes escaped -- layer names contain them."""
    return esc(s).replace('"', "&quot;")


def slug(s):
    return "".join(ch if ch.isalnum() else "-" for ch in s).strip("-")


def rect(x, y, w, h, fill, rx=0, stroke=None, sw=1, opacity=None, name=None):
    a = f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" rx="{rx:g}" fill="{fill}"'
    if stroke:
        a += f' stroke="{stroke}" stroke-width="{sw:g}"'
    if opacity is not None:
        a += f' opacity="{opacity:g}"'
    if name:
        a += f' data-name="{att(name)}"'
    out.append(a + "/>")


def text(x, y, s, size=14, fill=None, weight="400", anchor="start", opacity=None):
    fill = fill or C["onSurface"]
    a = (f'<text x="{x:g}" y="{y:g}" font-family="{FONT}" font-size="{size:g}" '
         f'font-weight="{weight}" fill="{fill}" text-anchor="{anchor}"')
    if opacity is not None:
        a += f' opacity="{opacity:g}"'
    out.append(a + f' data-name="{att(s[:40])}">{esc(s)}</text>')


def line(x1, y1, x2, y2, stroke, sw=1, dash=None):
    a = (f'<line x1="{x1:g}" y1="{y1:g}" x2="{x2:g}" y2="{y2:g}" '
         f'stroke="{stroke}" stroke-width="{sw:g}"')
    if dash:
        a += f' stroke-dasharray="{dash}"'
    out.append(a + "/>")


def path(d, stroke=None, fill="none", sw=2, cap="round", join="round"):
    a = f'<path d="{d}" fill="{fill}"'
    if stroke:
        a += f' stroke="{stroke}" stroke-width="{sw:g}" stroke-linecap="{cap}" stroke-linejoin="{join}"'
    out.append(a + "/>")


def g_open(name):
    out.append(f'<g id="{slug(name)}" data-name="{att(name)}">')


def g_close():
    out.append("</g>")


# ---------------------------------------------------------------- primitives

def button(x, y, w, label, filled=True, disabled=False, h=48, icon=None):
    """A Material-ish button. Disabled state is what the loading screen shows."""
    if disabled:
        rect(x, y, w, h, C["surfaceVar"], rx=h / 2)
        text(x + w / 2, y + h / 2 + 5, label, 15, C["muted"], "600", "middle")
        return
    if filled:
        rect(x, y, w, h, C["primary"], rx=h / 2)
        text(x + w / 2, y + h / 2 + 5, label, 15, "#FFFFFF", "600", "middle")
    else:
        rect(x, y, w, h, "none", rx=h / 2, stroke=C["primary"])
        text(x + w / 2, y + h / 2 + 5, label, 15, C["primary"], "600", "middle")


def progress(x, y, w, frac, track=None, fill=None, h=4):
    rect(x, y, w, h, track or C["surfaceVar"], rx=h / 2)
    if frac > 0:
        rect(x, y, max(w * frac, h), h, fill or C["primary"], rx=h / 2)


def tick(x, y, colour="#FFFFFF", s=1.0):
    path(f"M {x:g} {y:g} l {3.5 * s:g} {3.5 * s:g} l {6.5 * s:g} {-7 * s:g}", stroke=colour, sw=2)


def checkbox(x, y, checked, size=18):
    if checked:
        rect(x, y, size, size, C["primary"], rx=4)
        tick(x + 4, y + 9)
    else:
        rect(x, y, size, size, "none", rx=4, stroke=C["muted"], sw=1.5)


def chevron_left(x, y, colour, s=5):
    path(f"M {x + s:g} {y - s:g} L {x:g} {y:g} L {x + s:g} {y + s:g}", stroke=colour, sw=1.8)


def spinner(cx, cy, r, colour):
    path(f"M {cx:g} {cy - r:g} A {r:g} {r:g} 0 1 1 {cx - r:g} {cy:g}", stroke=colour, sw=2.5)


def bottom_block(x, y, h, fill, rx=20):
    """A block that runs to the bottom of the frame, matching its rounded corners."""
    rect(x, y, FW - 2, h, fill, rx=rx)
    rect(x, y, FW - 2, h - rx, fill)


def frame(x, y, index, title, captions):
    """Phone artboard shell plus the label above and caption block below."""
    text(x, y - 14, f"{index}  {title}", 15, C["onSurface"], "700")
    rect(x, y, FW, FH, C["parchment"], rx=20, stroke=C["outline"])
    for i, cap in enumerate(captions):
        text(x, y + FH + 28 + i * 17, cap, 11.5, C["onSurfaceVar"])


def status_bar(x, y):
    text(x + 18, y + 20, "9:41", 11, C["onSurfaceVar"], "600")
    for i in range(3):
        rect(x + FW - 46 + i * 10, y + 12, 6, 9, C["onSurfaceVar"], rx=1, opacity=0.5 + i * 0.2)


def app_bar(x, y, title, right=None, back=True):
    if back:
        path(f"M {x + 28:g} {y + 22:g} L {x + 18:g} {y + 28:g} L {x + 28:g} {y + 34:g}",
             stroke=C["onSurface"], sw=1.8)
        line(x + 18, y + 28, x + 32, y + 28, C["onSurface"], 1.8)
    text(x + (46 if back else 18), y + 33, title, 16, C["onSurface"], "600")
    if right:
        text(x + FW - 18, y + 33, right, 13, C["onSurfaceVar"], "500", "end")


def webview(x, y, h, label, mode="results", term="chopped tomatoes"):
    """The live Tesco page. Drawn schematically -- this is their UI, not ours."""
    rect(x + 1, y, FW - 2, h, C["surface"])
    rect(x + 1, y, FW - 2, 30, C["webChrome"])
    text(x + 14, y + 19, label, 10, C["muted"])
    line(x + 1, y + 30, x + FW - 1, y + 30, C["webLine"])

    if mode == "signin":
        rect(x + 40, y + 90, FW - 80, 1, C["webLine"])
        text(x + FW / 2, y + 80, "Sign in to your Tesco account", 13, C["onSurface"], "600", "middle")
        for i, lbl in enumerate(["Email", "Password"]):
            rect(x + 40, y + 110 + i * 58, FW - 80, 40, C["surface"], rx=6, stroke=C["webLine"], sw=1.5)
            text(x + 52, y + 135 + i * 58, lbl, 12, C["muted"])
        rect(x + 40, y + 232, FW - 80, 40, C["webBlue"], rx=20)
        text(x + FW / 2, y + 257, "Sign in", 13, "#FFFFFF", "600", "middle")
        return

    if mode == "loading":
        spinner(x + FW / 2, y + 120, 14, C["muted"])
        text(x + FW / 2, y + 160, "Loading results...", 12, C["muted"], "400", "middle")
        for i in range(3):
            ry = y + 200 + i * 74
            rect(x + 14, ry, 56, 56, C["webChrome"], rx=6)
            rect(x + 84, ry + 8, 150, 10, C["webChrome"], rx=5)
            rect(x + 84, ry + 26, 96, 10, C["webChrome"], rx=5)
        return

    text(x + 14, y + 52, f'Results for "{term}"', 12, C["onSurfaceVar"], "600")
    for i, (name, size, price) in enumerate([
        ("Tesco Chopped Tomatoes", "400g", "45p"),
        ("Tesco Italian Chopped Tomatoes", "400g", "85p"),
        ("Napolina Chopped Tomatoes", "390g", "£1.10"),
    ]):
        ry = y + 68 + i * 78
        rect(x + 14, ry, FW - 28, 70, C["surface"], rx=8, stroke=C["webLine"])
        rect(x + 24, ry + 8, 54, 54, C["webChrome"], rx=6)
        text(x + 88, ry + 24, name[:26], 11.5, C["onSurface"], "500")
        text(x + 88, ry + 40, size, 10.5, C["muted"])
        text(x + 88, ry + 56, price, 12, C["onSurface"], "700")
        rect(x + FW - 82, ry + 20, 56, 30, "none", rx=15, stroke=C["webBlue"], sw=1.5)
        text(x + FW - 54, ry + 40, "Add", 12, C["webBlue"], "600", "middle")


def action_bar(x, y, h, item, qty, done, total, state="ready"):
    """The one part of this screen that is ours to design."""
    bottom_block(x + 1, y, h, C["parchment"])
    line(x + 1, y, x + FW - 1, y, C["outline"], 1.5)

    progress(x + 16, y + 18, 232, done / total)
    text(x + FW - 16, y + 23, f"{done} of {total}", 11, C["onSurfaceVar"], "600", "end")

    chevron_left(x + 18, y + 50, C["muted"] if done <= 1 else C["onSurfaceVar"])
    text(x + 40, y + 55, item, 15, C["onSurface"], "600")
    line(x + 40, y + 60, x + 40 + len(item) * 7.6, y + 60, C["muted"], 1, dash="2 3")
    text(x + FW - 16, y + 55, qty, 14, C["onSurfaceVar"], "500", "end")

    button(x + 16, y + 76, 108, "Skip", filled=False)
    if state == "loading":
        button(x + 136, y + 76, 208, "Loading...", disabled=True)
    else:
        button(x + 136, y + 76, 208, "Added  ✓")


# ------------------------------------------------------------------ screens

def screen_shopping(x, y):
    g_open("01 Shopping list")
    frame(x, y, "01", "Shopping list", [
        "The launch point. Quantities are already aggregated across every",
        "recipe in the week, which is exactly what you need in front of you",
        "while choosing pack sizes.",
    ])
    status_bar(x, y)
    app_bar(x, y + 28, "Week of 7 Sept", back=False)

    text(x + 18, y + 106, "18 of 24 left", 12, C["onSurfaceVar"])
    progress(x + 18, y + 118, FW - 36, 6 / 24, h=6)

    button(x + 16, y + 140, 182, "Fill Tesco basket", h=42)
    button(x + 208, y + 140, 100, "Copy", filled=False, h=42)

    rect(x + 16, y + 196, FW - 32, 42, C["surface"], rx=8, stroke=C["outline"])
    text(x + 30, y + 222, "Add something else", 13, C["muted"])

    rows = [
        ("Fruit & veg", None, None, None),
        (None, "chopped tomatoes", "600 g", False),
        (None, "onions", "3", False),
        (None, "garlic", "1 bulb", True),
        (None, "flat-leaf parsley", "1 bunch", False),
        ("Meat & fish", None, None, None),
        (None, "chicken breast", "900 g", False),
        (None, "smoked bacon", "6 rashers", True),
        ("Store cupboard", None, None, None),
        (None, "olive oil", "as needed", False),
    ]
    cy = y + 262
    for header, name, qty, checked in rows:
        if header:
            text(x + 18, cy + 14, header, 12, C["primary"], "700")
            cy += 30
            continue
        checkbox(x + 18, cy, checked)
        colour = C["muted"] if checked else C["onSurface"]
        text(x + 48, cy + 9, name, 13.5, colour, "500")
        if checked:
            line(x + 48, cy + 5, x + 48 + len(name) * 6.9, cy + 5, C["muted"], 1)
        text(x + FW - 18, cy + 9, qty, 12, C["onSurfaceVar"], "400", "end")
        cy += 34

    bottom_block(x + 1, y + FH - 62, 61, C["parchment"])
    line(x + 1, y + FH - 62, x + FW - 1, y + FH - 62, C["outline"])
    for i, (lbl, active) in enumerate([("Recipes", False), ("Plan", False),
                                       ("Shopping", True), ("Import", False)]):
        nx = x + 45 + i * 90
        colour = C["primary"] if active else C["muted"]
        if active:
            rect(nx - 26, y + FH - 52, 52, 26, C["primaryCont"], rx=13)
        rect(nx - 8, y + FH - 45, 16, 12, colour, rx=2, opacity=0.85)
        text(nx, y + FH - 14, lbl, 10, colour, "600" if active else "400", "middle")
    g_close()


def screen_signin(x, y):
    g_open("02 First run, not signed in")
    frame(x, y, "02", "First run", [
        "You sign in on Tesco's own page; the app never sees the credentials.",
        "Cookies persist, so this screen appears once and later runs open",
        "straight onto item 1. Start stays disabled until the page settles.",
    ])
    status_bar(x, y)
    app_bar(x, y + 28, "Fill Tesco basket", right="24 items")
    webview(x, y + 84, FH - 84 - 116, "tesco.com/groceries/en-GB", mode="signin")

    by = y + FH - 116
    bottom_block(x + 1, by, 115, C["parchment"])
    line(x + 1, by, x + FW - 1, by, C["outline"], 1.5)
    text(x + 18, by + 30, "Sign in above, then start", 14, C["onSurface"], "600")
    text(x + 18, by + 50, "24 items to add. Nothing is ordered for you.", 11.5, C["onSurfaceVar"])
    button(x + 16, by + 62, FW - 32, "Start with chopped tomatoes")
    g_close()


def screen_item(x, y):
    g_open("03 Item in progress")
    frame(x, y, "03", "Item in progress  ★", [
        "The hero state. Two taps per item: Add inside Tesco's page (their",
        "stepper, so two tins is their control), then Added here to advance.",
        "Quantity sits on the right because it is what you are shopping to.",
        "Dotted underline on the name signals it is editable -- see 05.",
    ])
    status_bar(x, y)
    app_bar(x, y + 28, "Fill Tesco basket", right="7 of 24")
    webview(x, y + 84, FH - 84 - 140, "tesco.com/groceries/en-GB/search?query=chopped+tomatoes")
    action_bar(x, y + FH - 140, 139, "chopped tomatoes", "600 g", 7, 24)
    g_close()


def screen_loading(x, y):
    g_open("04 Advancing to next item")
    frame(x, y, "04", "Advancing", [
        "Added is disabled while the next search loads, so an impatient",
        "double-tap cannot skip an item silently. It re-enables on",
        "onPageFinished, a signal the activity already has.",
    ])
    status_bar(x, y)
    app_bar(x, y + 28, "Fill Tesco basket", right="8 of 24")
    webview(x, y + 84, FH - 84 - 140, "tesco.com/groceries/en-GB/search?query=onions", mode="loading")
    action_bar(x, y + FH - 140, 139, "onions", "3", 8, 24, state="loading")
    g_close()


def screen_edit(x, y):
    g_open("05 Editing the search term")
    frame(x, y, "05", "Editing the term", [
        "The parser turns \"2 garlic cloves\" into \"garlic\", which searches",
        "fine, but some lines come out awkward. Tapping the name lets you",
        "retype without losing your place in the queue.",
    ])
    status_bar(x, y)
    app_bar(x, y + 28, "Fill Tesco basket", right="9 of 24")
    webview(x, y + 84, FH - 84 - 336, "tesco.com/groceries/en-GB/search?query=garlic",
            term="garlic")

    by = y + FH - 336
    rect(x + 1, by, FW - 2, 116, C["parchment"])
    line(x + 1, by, x + FW - 1, by, C["outline"], 1.5)
    text(x + 18, by + 26, "Search Tesco for", 11, C["onSurfaceVar"], "600")
    rect(x + 16, by + 36, FW - 32, 46, C["surface"], rx=8, stroke=C["primary"], sw=2)
    text(x + 30, by + 65, "garlic bulb", 15, C["onSurface"], "500")
    line(x + 96, by + 46, x + 96, by + 72, C["primary"], 1.5)
    text(x + FW - 30, by + 65, "✕", 14, C["muted"], "400", "end")
    text(x + 18, by + 100, "Was: garlic  ·  from \"2 garlic cloves\"", 11, C["muted"])

    bottom_block(x + 1, by + 116, 220, "#D6D2CA")
    for r in range(3):
        keys = [10, 9, 7][r]
        kw = (FW - 24 - (keys - 1) * 6) / keys
        for k in range(keys):
            kx = x + 12 + (FW - 24 - keys * kw - (keys - 1) * 6) / 2 + k * (kw + 6)
            rect(kx, by + 140 + r * 52, kw, 42, C["surface"], rx=5)
    rect(x + 96, by + 296, 168, 34, C["surface"], rx=5)
    rect(x + FW - 92, by + 296, 80, 34, C["primary"], rx=5)
    text(x + FW - 52, by + 318, "Search", 12, "#FFFFFF", "600", "middle")
    g_close()


def screen_queue(x, y):
    g_open("06 Full queue sheet")
    frame(x, y, "06", "Full queue", [
        "Opened from the counter in the app bar. Shows what is done, what",
        "was skipped and what is left, and lets you jump to any item --",
        "which is also the undo path when you tap Added by mistake.",
    ])
    status_bar(x, y)
    app_bar(x, y + 28, "Fill Tesco basket", right="7 of 24")
    webview(x, y + 84, FH - 84, "tesco.com/groceries/en-GB/search?query=chopped+tomatoes")
    rect(x + 1, y + 84, FW - 2, FH - 85, C["scrim"], opacity=0.45)

    sy = y + 210
    rect(x + 1, sy, FW - 2, FH - (sy - y) - 1, C["parchment"], rx=20)
    rect(x + FW / 2 - 20, sy + 10, 40, 4, C["outline"], rx=2)
    text(x + 18, sy + 44, "Your list", 17, C["onSurface"], "700")
    text(x + FW - 18, sy + 44, "7 of 24 done", 12, C["onSurfaceVar"], "500", "end")
    line(x + 18, sy + 58, x + FW - 18, sy + 58, C["outline"])

    items = [
        ("added", "chicken breast", "900 g"),
        ("added", "smoked bacon", "6 rashers"),
        ("skipped", "samphire", "1 pack"),
        ("current", "chopped tomatoes", "600 g"),
        ("pending", "onions", "3"),
        ("pending", "garlic", "1 bulb"),
        ("pending", "flat-leaf parsley", "1 bunch"),
        ("pending", "olive oil", "as needed"),
    ]
    cy = sy + 78
    for state, name, qty in items:
        if state == "added":
            rect(x + 18, cy, 18, 18, C["success"], rx=9)
            tick(x + 22, cy + 9)
            colour, weight = C["muted"], "400"
        elif state == "skipped":
            rect(x + 18, cy, 18, 18, "none", rx=9, stroke=C["muted"], sw=1.5)
            line(x + 23, cy + 9, x + 31, cy + 9, C["muted"], 1.5)
            colour, weight = C["muted"], "400"
        elif state == "current":
            rect(x + 12, cy - 6, FW - 24, 30, C["primaryCont"], rx=8)
            rect(x + 18, cy, 18, 18, C["primary"], rx=9)
            rect(x + 24, cy + 6, 6, 6, C["parchment"], rx=3)
            colour, weight = C["onPrimCont"], "700"
        else:
            rect(x + 18, cy, 18, 18, "none", rx=9, stroke=C["outline"], sw=1.5)
            colour, weight = C["onSurface"], "400"
        text(x + 48, cy + 13, name, 13.5, colour, weight)
        text(x + FW - 18, cy + 13, qty, 12, C["onSurfaceVar"] if state != "current" else C["onPrimCont"],
             "400", "end")
        cy += 36
    g_close()


def screen_summary(x, y):
    g_open("07 Run summary")
    frame(x, y, "07", "Summary", [
        "Added is self-reported, so the summary says so plainly and points",
        "at Tesco's own basket count as the real check. Ticking the added",
        "items off the shopping list is offered, never done silently.",
    ])
    status_bar(x, y)
    app_bar(x, y + 28, "Fill Tesco basket", right="Done")
    webview(x, y + 84, FH - 84, "tesco.com/groceries/en-GB/trolley")
    rect(x + 1, y + 84, FW - 2, FH - 85, C["scrim"], opacity=0.45)

    sy = y + 250
    rect(x + 1, sy, FW - 2, FH - (sy - y) - 1, C["parchment"], rx=20)
    rect(x + FW / 2 - 20, sy + 10, 40, 4, C["outline"], rx=2)

    rect(x + FW / 2 - 26, sy + 34, 52, 52, C["primaryCont"], rx=26)
    tick(x + FW / 2 - 11, sy + 61, C["primary"], s=2.2)
    text(x + FW / 2, sy + 116, "21 added, 3 skipped", 18, C["onSurface"], "700", "middle")
    text(x + FW / 2, sy + 138, "Check the basket before you pay.", 12, C["onSurfaceVar"], "400", "middle")

    rect(x + 16, sy + 158, FW - 32, 92, C["secondCont"], rx=10)
    text(x + 30, sy + 180, "Skipped -- add these yourself", 11.5, C["onSurface"], "700")
    for i, nm in enumerate(["samphire", "fresh dill", "sourdough loaf"]):
        text(x + 30, sy + 202 + i * 18, "•  " + nm, 11.5, C["onSurfaceVar"])

    button(x + 16, sy + 268, FW - 32, "View basket in Tesco")
    button(x + 16, sy + 326, FW - 32, "Tick 21 items off my list", filled=False)
    g_close()


def screen_foundations(x, y):
    g_open("08 Foundations")
    frame(x, y, "08", "Foundations", [
        "Colours are read straight out of ui/theme/Theme.kt, so the mockup",
        "and the running app cannot drift. Everything sits on an 8pt grid.",
    ])
    text(x + 20, y + 40, "Colour", 15, C["onSurface"], "700")
    swatches = [
        ("primary", C["primary"], "#2F5D3A"),
        ("primaryContainer", C["primaryCont"], "#CDE6D2"),
        ("secondary", C["secondary"], "#B4562F"),
        ("secondaryContainer", C["secondCont"], "#FFDBCD"),
        ("background", C["parchment"], "#FBF8F3"),
        ("surfaceVariant", C["surfaceVar"], "#E0E5DC"),
        ("onSurfaceVariant", C["onSurfaceVar"], "#444842"),
        ("added / success", C["success"], "#2E7D32"),
    ]
    for i, (name, val, hexs) in enumerate(swatches):
        sy = y + 54 + i * 34
        rect(x + 20, sy, 34, 26, val, rx=6, stroke=C["outline"])
        text(x + 64, sy + 12, name, 12, C["onSurface"], "500")
        text(x + 64, sy + 24, hexs, 10.5, C["muted"])

    text(x + 20, y + 356, "Type", 15, C["onSurface"], "700")
    for i, (label, size, weight) in enumerate([
        ("Title  17 / 700", 17, "700"),
        ("Item name  15 / 600", 15, "600"),
        ("Body  13.5 / 400", 13.5, "400"),
        ("Quantity  12 / 500", 12, "500"),
        ("Label  11 / 600", 11, "600"),
    ]):
        text(x + 20, y + 382 + i * 30, label, size, C["onSurface"], weight)

    text(x + 20, y + 552, "Queue states", 15, C["onSurface"], "700")
    states = [("Added", "added"), ("Skipped", "skipped"), ("Current", "current"), ("Pending", "pending")]
    for i, (label, state) in enumerate(states):
        sy = y + 574 + i * 32
        if state == "added":
            rect(x + 22, sy, 18, 18, C["success"], rx=9)
            tick(x + 26, sy + 9)
        elif state == "skipped":
            rect(x + 22, sy, 18, 18, "none", rx=9, stroke=C["muted"], sw=1.5)
            line(x + 27, sy + 9, x + 35, sy + 9, C["muted"], 1.5)
        elif state == "current":
            rect(x + 22, sy, 18, 18, C["primary"], rx=9)
            rect(x + 28, sy + 6, 6, 6, C["parchment"], rx=3)
        else:
            rect(x + 22, sy, 18, 18, "none", rx=9, stroke=C["outline"], sw=1.5)
        text(x + 52, sy + 13, label, 12.5, C["onSurface"])

    text(x + 20, y + 712, "Touch targets 48dp  ·  Gutters 16dp", 11, C["muted"])
    g_close()


# ------------------------------------------------------------------- render

out.append(
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{CANVAS_W}" height="{CANVAS_H}" '
    f'viewBox="0 0 {CANVAS_W} {CANVAS_H}" font-family="{FONT}">'
)
rect(0, 0, CANVAS_W, CANVAS_H, C["canvas"], name="Canvas")

g_open("Title")
text(80, 66, "Pantry — Tesco basket, deep-link flow", 30, C["onSurface"], "700")
text(80, 94, "Manual add, one item at a time. No DOM automation: the only "
             "dependency is the search URL.", 14, C["onSurfaceVar"])
g_close()

screens = [screen_shopping, screen_signin, screen_item, screen_loading,
           screen_edit, screen_queue, screen_summary, screen_foundations]
for i, fn in enumerate(screens):
    fn(COLS[i % 4], ROWS[i // 4])

g_open("Flow arrows")
for i in range(3):
    ax = COLS[i] + FW + 14
    ay = ROWS[0] + FH / 2
    path(f"M {ax:g} {ay:g} L {ax + 52:g} {ay:g}", stroke=C["muted"], sw=1.5)
    path(f"M {ax + 45:g} {ay - 5:g} L {ax + 52:g} {ay:g} L {ax + 45:g} {ay + 5:g}",
         stroke=C["muted"], sw=1.5)
for i in range(2):
    ax = COLS[i] + FW + 14
    ay = ROWS[1] + FH / 2
    path(f"M {ax:g} {ay:g} L {ax + 52:g} {ay:g}", stroke=C["muted"], sw=1.5)
    path(f"M {ax + 45:g} {ay - 5:g} L {ax + 52:g} {ay:g} L {ax + 45:g} {ay + 5:g}",
         stroke=C["muted"], sw=1.5)
g_close()

out.append("</svg>")

with open("pantry-tesco-flow.svg", "w", encoding="utf-8") as f:
    f.write("\n".join(out))
print("wrote pantry-tesco-flow.svg")
