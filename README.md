# PillTap

Android and Wear OS medication tracker with optional NFC confirmations.

## Features

- Add as many medicines as you take (including zero).
- Set multiple independent daily times for each medicine, e.g. 08:00, 14:00 and 20:00.
- Pause, edit or remove a medicine without erasing its existing intake history.
- Confirm each scheduled dose separately from the app, a reminder action or a paired Wear OS watch.
- Review intake history by medicine, planned time, actual time and confirmation source.
- One reminder per planned dose; taken doses stop reminding for that day. Reminders are restored after a restart or time/timezone change.
- A generic PillTap NFC tag confirms the nearest untaken dose of the day. If several doses are close in time, the phone asks which one was taken.

## Getting started

1. Open **Lieky**, choose **Pridať liek**, enter its name and add the scheduled times.
2. On **Dnes**, confirm individual doses after actually taking them.
3. Review entries in **História**. You can undo today's confirmations directly from **Dnes**.
4. On **Nastavenia**, optionally configure an NFC tag and allow exact alarms for more precise reminders.

The database is stored locally on the Android phone. When upgrading from v0.1, existing anonymous daily intake records are kept as **Pôvodný záznam**. No medicine is created automatically.

**NFC/watch note:** A generic tag does not identify which medicine is being taken. On Wear OS you can select an individual dose, including when NFC reading is unavailable. Android may delay reminders if exact-alarm access is not granted.
