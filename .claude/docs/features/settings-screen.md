# Settings screen

Settings are a first page of categories, each opening a page of its own, the way the system's own
Settings, One UI and HyperOS lay theirs out. A category is a coloured disc, a title and a line
saying what is on its page; a page is its settings in rounded groups under small headings, every
row with an icon in the page's colour.

## Coupled with

None known.

## Where it lives

| File | Job |
|---|---|
| `activities/SettingsActivity.kt` | The screen: every setting's setup, `setupPages`, `pageShown` |
| `helpers/SettingsPages.kt` | Which page is up, the links to them, the slide between them, back |
| `views/SettingsPage.kt` | `SettingsPage` (a category's title and hue) and `SettingsLink` (a row opening one) |
| `views/SettingsGroup.kt` | A heading over a rounded card of rows, with hairlines between them |
| `layout/activity_settings.xml` | Every page at once; `layout/settings_link.xml` for a link |
| `views/InfoPopup.kt` | The (i) note beside a setting (`View.explains(text)`), used by Tabs |

The pages: Look & feel, General, Thumbnails, Folders, Fullscreen media, Gestures & zoom, Videos,
Security, Files & recycle bin, Storage & backup. **A page takes an upstream title wherever one
fits**, since those are translated and the fork's own strings are English only; the lines under
them are the fork's. The hues are fixed (`settings_hue_*`), not the theme's accent, so a page is
known by its colour; they are carried towards white on a dark theme, or black on a light one, as far
as they have to be to read on the cards. A card is lifted off a dark background like the glass, and
sunk a shade below a light one (`settingsCardColor`), where there is nothing to lift it towards.

## How it works

**Every page is in the one layout, and only one is shown.** A row keeps the id its setup code in
`SettingsActivity` has always looked it up by, so that code is upstream's, unchanged, and
`activity_settings.xml` still reads as the list of settings. A `SettingsGroup`'s rows are simply its
children in the layout - `onFinishInflate` moves them onto its card.

A `SettingsLink` names its page in `app:opens` and takes that page's title and hue, so the two
cannot disagree; `SettingsPages` finds the links and wires them. Opening a page fades and slides the
content the way the link points, and back - the arrow, or the system's - returns to the first page
where it was left scrolled. The open page survives a rotation.

A row's first child, where it is an image, is its icon, and its group tints it. Rows are
LinearLayouts of icon, then texts or a switch; the Tabs row keeps a RelativeLayout around its switch
for the (i), which is placed at runtime just past the label.

The heading at the top of the content is the open page's name, and hands over to the bar's title as
it scrolls under the bar. The bar floats over the pages, softened by the same edge fades the grids
use.

## What breaks silently

- **Only the page that can be seen is painted.** `updateTextColors` walks whatever it is handed,
  and handing it the whole screen was 22ms of the 34ms the screen took to come up. A page is painted
  as it comes up (`pageShown`); everything else only has its colours re-read on resume.
- **A group whose every row is hidden disappears with them** (`refreshGroups`), which only happens
  after the setup functions have decided what this platform offers - so `setupPages` runs last.
- A row added to the layout wants its setup in `SettingsActivity` and nothing else, but a row added
  outside any page is never shown.
