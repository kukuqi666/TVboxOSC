---
description: What TVboxOSC is, supported devices, the three documentation entry points and a reading order.
---

# Introduction

TVboxOSC is a player for Android TV and Android phones. The App ships without any content source; you add a VOD or live TV configuration and then load your own sites and channels.

Spiders fetch data and the App renders it. A spider returns categories, lists and playback info using the Result format, so Java, Python and QuickJS all share one data contract.

## Devices

- **Android TV**: operated with a remote control, supports large screens and EPG.
- **Android phones**: touch operated, supports portrait, landscape and picture-in-picture.

## Documentation entry points

| Entry | For | Start with |
| --- | --- | --- |
| [Spider integration](/spider/load) | Building or fixing a spider | Loading a spider → Three-language demo → Methods and timing |
| [Configuration](/config/examples) | Maintaining VOD, live and service configuration | Full examples → the matching object fields |
| [Local API](/local/connection) | Driving the App from scripts, browsers or other devices | Connection → the endpoint you need |
| [App features](/features/features) | First-time users who want an overview | Feature list → [Playback commands](/commands/start) |

## Suggested order

1. [Quick start](/guide/start/getting-started): add a first configuration and confirm playback.
2. [Spider integration](/spider/load): when you need your own source, start from the three-language demo.
3. [Configuration](/config/examples): complete fields for sites, parsing, live TV and shared objects.

:::tip
The `example.com` host, IP addresses and device identifiers in these pages are illustrative only. Replace them with your own source and real data before going live.
:::

## Before reporting an issue

Confirm three things first: the configuration loads, the spider test page returns valid data, and the App can actually play the result. A passing module test or a correct URL does not guarantee playback.
