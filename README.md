# OLA Battery Alert

Realtime Ola widget reader with spatial ML Kit OCR fallback.

1. Select the Ola battery widget
2. Allow overlay + notifications
3. Start background monitor
4. Alarm fires at your charge limit

Version 2.1 prioritizes a 0–100 number with a nearby `%` glyph, detects the
green lightning icon beside it, and shows the live value in green while
charging or blue while not charging.

Version 2.2 keeps one bound widget host alive and retries after provider
updates, preventing OCR from repeatedly reading an old cached widget frame.

Version 2.3 is widget-number OCR only. It ignores widget text, content
descriptions and other text fallbacks while a widget is bound, and accepts only
a visible 0–100 number spatially paired with `%`.
