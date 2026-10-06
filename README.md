# Snapshot for Burp Suite

[![Build](https://github.com/noverdy/burp-snapshot/actions/workflows/build.yml/badge.svg)](https://github.com/noverdy/burp-snapshot/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/noverdy/burp-snapshot?display_name=tag)](https://github.com/noverdy/burp-snapshot/releases/latest)

A Burp Suite extension that turns a request and response into a clean PNG for your pentest report. Box what matters, add notes, and secrets get blurred for you. No more screenshot, paint tool and manual blurring.

![Snapshot demo](docs/demo.gif)

[Watch the demo in full quality (25 s)](docs/demo.mp4)

## Features

- Click a parameter, header, cookie or JSON key to box it and add a note.
- Automatic redaction of auth headers, cookies and tokens, keeping the last 4 characters.
- AI markup (Burp Pro): describe the finding and Burp AI boxes the evidence, writes the notes, title and caption.
- Intruder results as a table.
- Burp's own light and dark colors, side by side or stacked, with a tiled watermark.
- Copy or save as PNG, or press `⌘⇧C` to copy without opening a window.
- Drafts and exported snapshots saved in your Burp project.

> [!IMPORTANT]
> Redacted text never reaches the image. The blur is drawn over random placeholder characters, so it can't be reversed.

| Light | Dark |
|---|---|
| ![Light theme with callouts](docs/images/light-callout.png) | ![Dark theme](docs/images/dark-showcase.png) |
| **Stacked with legend** | **Intruder results** |
| ![Stacked layout with legend](docs/images/light-legend-stacked.png) | ![Intruder results table](docs/images/results-table.png) |

## Install

Download the jar from the [latest release](https://github.com/noverdy/burp-snapshot/releases/latest), then add it in Burp under **Extensions → Installed → Add** (type **Java**). Needs Burp 2026.7 or later, Professional or Community.

## Usage

Right-click any request (Proxy, Repeater, Logger, Intruder…) and choose **Extensions → Snapshot**. Pick **Snapshot as results table…** when several rows are selected.

![Right-click menu in Proxy history](docs/images/burp-context-menu.png)

| Shortcut | Action |
|---|---|
| `⌘⇧X` | Open the selected request in Snapshot |
| `⌘⇧C` | Quick copy: PNG to the clipboard, no window |
| `M` / `R` | Mark or Redact tool: click a value, or drag across text |
| `⌘Z` / `⇧⌘Z` | Undo / redo |
| `⇧⌘C` / `⌘S` | Copy image / save PNG (inside the editor) |

Use `Ctrl` instead of `⌘` on Windows and Linux. Change the two global hotkeys from the gear in the **Snapshot** tab.

**AI markup** needs Burp Pro with **Use AI** enabled for Snapshot in **Extensions → Installed**. Burp AI only sees what the image shows, with redacted values replaced.

**Drafts and exported** live in the **Snapshot** tab. Drafts keep your last 20 unexported snapshots, and anything you copied or saved stays under Exported. Burp Community projects are temporary, so they don't survive a restart.

## Build

```bash
./gradlew build      # jar in build/libs/
./gradlew demo       # re-record the demo video (needs ffmpeg and Burp)
```

Pushing a `v*` tag builds the jar and publishes a release.
