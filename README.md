# Snapshot for Burp Suite

[![Build](https://github.com/noverdy/burp-snapshot/actions/workflows/build.yml/badge.svg)](https://github.com/noverdy/burp-snapshot/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/noverdy/burp-snapshot?display_name=tag)](https://github.com/noverdy/burp-snapshot/releases/latest)

A Burp Suite extension that turns a request and response into a clean PNG for your pentest report. You box the parameters that matter, write a note next to each, and the extension blurs the secrets. It replaces the usual routine of taking a screenshot, drawing squares in a paint tool and blurring tokens by hand.

![Snapshot demo](docs/demo.gif)

[Watch the full demo (67 s)](docs/demo.mp4)

## Features

- Request and response side by side or stacked, drawn in Burp's own light or dark editor colors with line numbers.
- Click a parameter, cookie, header or JSON key to box the whole key and value, then type a note. Notes appear as callouts you can drag, or as a numbered legend under the image.
- Automatic redaction of `Authorization`, `Cookie`, `Set-Cookie`, API key headers and any parameter that looks like a password, token or session. Long values keep their last 4 characters so you can tell two tokens apart. Click any other value to redact it as well.
- A tiled watermark with `{date}` and `{host}` placeholders and an optional logo.
- Intruder results as a table, with the payloads worked out from the selected requests.
- Copy to the clipboard or save a PNG at 1x, 2x or 3x.
- Hotkeys: copy a snapshot straight to the clipboard without opening a window, or open the editor.
- Autosave into the Burp project, with a **Snapshot** tab to reopen drafts and exported snapshots.

> [!IMPORTANT]
> The extension redacts text before it draws anything. Real characters never reach the image, and the blur is painted over random placeholder text, so nobody can un-blur it.

| Light, with callouts | Dark |
|---|---|
| ![Light theme with callouts](docs/images/light-callout.png) | ![Dark theme](docs/images/dark-showcase.png) |
| **Stacked, numbered legend, solid redaction** | **Intruder results table** |
| ![Stacked layout with legend](docs/images/light-legend-stacked.png) | ![Intruder results table](docs/images/results-table.png) |

## Install

1. Download `burp-snapshot-<version>.jar` from the [latest release](https://github.com/noverdy/burp-snapshot/releases/latest).
2. In Burp, open **Extensions → Installed → Add**, set the type to **Java** and pick the jar.

It runs in Burp Suite Professional and Community. The extension is built against Montoya API 2026.7, so use Burp 2026.7 or later.

## Usage

### Request and response

Select a request in Proxy, Repeater, Logger, Target or any other tool, then right-click and choose **Extensions → Snapshot → Snapshot request/response…**

![Right-click menu in Proxy history](docs/images/burp-context-menu.png)

In the Snapshot window:

| Action | How |
|---|---|
| Box a parameter and add a note | Mark tool (`M`), click the parameter |
| Box any text | Mark tool, drag across it |
| Redact or un-redact a value | Redact tool (`R`), click it or drag across text |
| Move a note | Drag the callout |
| Change color, edit or delete a mark | Right-click the box |
| Hide a header | Right-click the header line |
| Zoom | Pinch, or `⌘`/`Ctrl` + scroll |
| Undo / redo | `⌘Z` / `⇧⌘Z` (`Ctrl` on Windows and Linux) |
| Copy image / save PNG | `⇧⌘C` / `⌘S` |

> [!TIP]
> Select text in Burp's message editor before you right-click and the snapshot opens with that text already boxed.

### Hotkeys

| Hotkey | What it does |
|---|---|
| `⌘⇧C` / `Ctrl+Shift+C` | Quick copy: renders the selected request with your last settings and auto-redaction and copies the PNG. No window opens. |
| `⌘⇧X` / `Ctrl+Shift+X` | Opens the selected request in the Snapshot editor |

Both work in message editors, Proxy history, Intruder results, the site map and Organizer. With two or more rows selected, you get a results table. Text you selected in the editor is boxed already.

After a quick copy, a small notification shows a thumbnail with an **Open** button. The copy is saved under **Exported**, so you can fix it later. Quick copy skips the review step, so check the thumbnail: auto-redaction only knows the names in your redaction rules.

To change the keys or the quick copy title (default `{method} {path}`), click the gear in the **Snapshot** tab.

### Intruder results

In the attack results window, select two or more rows (Shift-click for a range), then right-click and choose **Extensions → Snapshot → Snapshot as results table…** The same item shows up in Proxy history and Logger.

![Right-click menu in Intruder results](docs/images/intruder-context-menu.png)

Type a string in the **Contains** box to add a ✓/✗ column, similar to Burp's grep match. Right-click a row to edit its payload label or hide it.

> [!NOTE]
> Burp doesn't share Intruder's payload columns with extensions. Snapshot compares the selected requests to find the payloads, including attacks with more than one payload position. If a label comes out wrong, fix it with **Edit payload…**

### Drafts and exported snapshots

Snapshot saves your work to the Burp project as you edit, so closing the window by accident doesn't lose anything. The **Snapshot** tab in Burp's top bar lists two kinds of entries:

- **Drafts** are snapshots you changed but haven't copied or saved yet. The tab keeps the 20 most recent.
- **Exported** are snapshots you copied or saved at least once. These are never removed automatically.

Double-click an entry to keep editing it. Editing an exported snapshot updates it in place. Opening a request without changing anything doesn't create a draft.

The project stores the request, the response, your edits (marks, notes, redactions, hidden headers, title) and that snapshot's settings, not the rendered image. Each snapshot keeps its own theme, layout, redaction rules and watermark, so changing them for a new snapshot leaves older ones alone. The tab renders a fresh preview when you select an entry.

> [!NOTE]
> Burp Community only has temporary projects, so drafts and exported snapshots are gone once Burp closes.

### Settings

The sidebar keeps the per-screenshot options at the top: theme, layout, mark color, note style, redaction style, watermark text and export scale. Redaction rules, hidden headers, body truncation, line wrapping and watermark styling sit in collapsible sections below. Settings belong to each snapshot. New snapshots start with the settings you used last, which Snapshot keeps in Burp's preferences across projects.

Response time comes from Burp's timing data, including Proxy history and the time Repeater shows under the response. For tools without timing data, the extension times requests itself while it's loaded. When no time is known, the sidebar says so under **Show response time**. The time appears in the header bar, or as a column in results tables.

## Build from source

You need JDK 21 or later.

```bash
./gradlew build      # jar lands in build/libs/
./gradlew samples    # renders the sample images into build/samples/
./gradlew demo       # re-records the demo video (needs ffmpeg and a local Burp install)
```

Pushing a tag such as `v1.0.0` makes GitHub Actions build the jar and attach it to a release.
