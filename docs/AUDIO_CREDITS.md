# Audio credits

The three timer chimes in `app/src/main/res/raw/` (`chime_soft_bell.wav`, `chime_wood_block.wav`, `chime_digital.wav`) were
synthesised from sine partials and envelopes by a small script, not sampled from any recording or library. They are original
work, dedicated to the public domain under **CC0 1.0**. No attribution is required and no third-party audio ships in the app.

They play through `SoundPool` (`core/audio/TimerChime.kt`) on the notification usage, so they follow the phone's volume and
silent mode. Settings → Comfort → Timer sound picks one or turns them off.
