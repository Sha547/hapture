# Hapture spring: Figma plugin

Applies a spring tuned in Hapture to the prototype interactions of the layers you select
(Smart Animate with Figma's *Custom spring* easing: mass, stiffness, damping).

## Install (development plugin)

1. Figma desktop: **Plugins → Development → Import plugin from manifest…**
2. Choose `manifest.json` in this folder.

## Use

- **Paste a spec:** in Hapture, open any experiment → Export → *Motion spec* (copies the JSON). Paste it into the plugin, select a layer that has a prototype connection, press **Apply**.
- **Live:** in Hapture, Export → *Start live sync*, then type the shown address (for example `http://192.168.1.20:8787`) and the 4-digit pairing code into the plugin and press **Connect**. Each change in the app is applied to whatever is selected.

The plugin only edits the `transition` of interactions that already exist; it never adds or removes connections.

## Status

The plugin code is syntax-checked but has not been run inside Figma from this repository's test setup, so treat the first run as a check.
