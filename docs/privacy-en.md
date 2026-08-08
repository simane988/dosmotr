---
layout: default
title: Privacy policy
permalink: /privacy/en/
lang: en
---

Last updated: 7 August 2026. Applies to the Dosmotr app (`com.g3ck0.dosmotr`) in every
distribution channel.

[Русская версия]({{ '/privacy/' | relative_url }})

## In short

Your library, watched marks, statuses, ratings and notes are stored **on your device
only**. The app has no accounts and no sign-in, so the author has no list of what you
watch and could not have one. Three things leave the device: a search query and a title
identifier, sent to the project's own backend when you search or refresh a title; and —
**only in builds from app stores, and only while it is not switched off in the settings** —
crash reports and anonymous counters.

## Who processes the data

The author of the app, a private individual; contact: {{ site.contact_email }}. The app is
developed in the open: the [source code]({{ site.repo_url }}) can be read in full,
including everything described below.

## Stored on the device and never sent anywhere

- the series and films you added, including manually created entries;
- watched marks for episodes and films, and statuses;
- your ratings and notes;
- app settings;
- JSON backups: automatic ones go to the app's private folder, or to a folder you picked
  yourself. A manual export writes the file wherever you point it. The app uploads none of
  these files anywhere; if the folder you picked is synchronised by a cloud client, that is
  your decision and that service's terms.

Uninstalling the app deletes all of it.

## What goes to the project's server

The app talks to exactly one server — the project's backend — and only in these cases:

| When | What is sent |
|---|---|
| Search | the query text and the results page number |
| Weekly trends (empty search) | nothing beyond the request itself |
| Opening or refreshing a title | the catalogue identifier of the title and a season number |
| Loading posters and artwork | the image path and size |

Along with the request goes what any web request carries: an IP address, the request path
and the app version in the User-Agent string. **Device identifiers, the advertising ID, an
install ID and your library are not sent — they are not part of the request at all.**
Requests are not linked into a profile: there is nothing to link them by, because there are
no accounts.

The server keeps technical access logs (IP address, time, request path, response code).
This is not analytics: the logs exist so that token guessing and automated attacks can be
blocked, and they are read by fail2ban. The web server log is kept for **no longer than
seven days** and is then overwritten; the application's own logs are bounded by size and
rotate out. Because the search query travels in the request URL, it also appears in that
log — the text of your search is physically present on the server for that period, in that
one form. It is not written to any database and is not correlated with anything.

For metadata about series and films the server queries an external catalogue (currently
TMDB). Only the search query or the title identifier is passed on — by the project's
server, not by your device; your phone's IP address and none of your data are sent there.

## Crash reports: store builds only

The app exists in two builds, and this is where they differ fundamentally.

**The build from RuStore or Google Play** contains Firebase Crashlytics and Firebase
Analytics (provided by Google LLC). While reporting is enabled it sends:

- crash reports: the stack trace, device model, Android version, app version;
- eleven anonymous counters — an event name and nothing more: `first_launch`,
  `title_added`, `episode_watched`, `search_performed`, `manual_add`, `import_started`,
  `import_finished`, `export_done`, `backup_auto_ok`, `notifications_allowed`,
  `about_opened`.

An event may carry **exactly one** value: `tv` or `movie`, i.e. which kind of title was
added. No other parameter exists: the list of events and the list of allowed values are
fixed in the source and enforced by tests, and an event outside the list is dropped before
it is sent.

Collection of the advertising ID (AAID) and of the Android ID is **disabled** in the app's
manifest — those are exactly the cross-app identifiers such data is usually linked by.

Reporting can be switched off: «О приложении» → «Отправлять отчёты о падениях». Collection
starts off and is enabled from the stored setting after the app starts.

**The build from [GitHub Releases]({{ site.releases_url }})** contains no Google components
at all — no Crashlytics, no Analytics, no other Google library. It has nothing to report
with, which is why it has no switch either. That is verified from the build's dependency
graph rather than promised.

## Never collected, in any build

Titles you added, watch progress, ratings, notes, backup contents, the list of installed
apps, contacts, location, phone number, email address, advertising ID, IMEI, files on the
device.

## Who the data is shared with

Nobody, except the case above: Google LLC as the provider of Crashlytics and Analytics —
in the store build only, and only while reporting is enabled. Data is not sold and is not
shared with ad networks or data brokers; the app contains no advertising SDKs.

## How to delete your data

Uninstall the app. Everything it stores lives on the device and goes with it; no deletion
request is needed and there is nowhere to send one, because the author holds no copy.
Backup files you saved into a folder of your own stay where you put them — delete them if
you no longer need them.

Crash reports already sent from a store build are retained by Firebase Crashlytics for a
limited period under that service's terms. To stop further ones, switch the toggle off in
«О приложении».

## Age of the audience

The app is intended for users aged **12 and over** and is not directed at children below
that age. It collects no personally identifying data and does not ask for an age.

## Permissions

- **Internet** — searching and refreshing title data.
- **Notifications** (optional) — the permission is requested ahead of time, for reminders
  about newly aired episodes. As of this revision the app sends no notifications at all:
  the feature is not built yet, so refusing currently affects nothing.
- The app requests no file access: export and import go through the system file picker,
  which grants access to the chosen file or folder only.

## Changes to this policy

Changes are published on this page with a new date; the edit history is visible in the
[project repository]({{ site.repo_url }}/commits/master/docs/privacy-en.md). If the set of
collected data changes, this page is updated before the corresponding version ships.

## Questions

{{ site.contact_email }} or the [project issues]({{ site.repo_url }}/issues).

---

[Home]({{ '/' | relative_url }}) ·
[Support the author]({{ '/support/' | relative_url }}) ·
[Русская версия]({{ '/privacy/' | relative_url }})
