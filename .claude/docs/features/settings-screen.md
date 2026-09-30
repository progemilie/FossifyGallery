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
| `helpers/SettingsPages.kt` | Which page is up, the links to them, the push between them, back |
| `helpers/SettingsSearch.kt` | The search at the top of the first page, and what it finds |
| `views/SettingsPage.kt` | `SettingsPage` (a category's title and hue) and `SettingsLink` (a row opening one) |
| `views/SettingsGroup.kt` | A heading over a rounded card of rows, with hairlines between them |
| `layout/activity_settings.xml` | Every page at once, in two scrollers; `layout/settings_link.xml` for a link |
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
cannot disagree; `SettingsPages` finds the links and wires them. A page only ever opens from the
first page and goes back to it, so the first page has a scroller of its own and the others share a
second: opening a page pushes the two across the whole width side by side, the way One UI's pages
move, both drawn for real, and a push that is turned back part way returns from where it got to. The
page coming in is painted and laid out before either moves. Back - the arrow, or the system's -
returns to the first page as it was left scrolled. The open page survives a rotation.

A row's first child, where it is an image, is its icon, and its group tints it. Rows are
LinearLayouts of icon, then texts or a switch; the Tabs row keeps a RelativeLayout around its switch
for the (i), which is placed at runtime just past the label.

Each scroller starts with its page's name, which hands over to the bar's title as it scrolls under
the bar, which fills in behind it. The bar floats over the pages, softened by the
same edge fades the grids use.

## Search

While anything is typed into the field at the top of the first page, its links give way to every
setting whose title holds the text (accents and case aside), under the page and heading it sits on.
What is searched is read off the rows as they stand at that moment - a row's title is the first
text it shows - so a row its setup has hidden is not found, and one it has retitled is found by its
new title. Picking a finding opens its page, scrolls the setting into view and washes it in the
page's hue for a moment. Back closes a page first, then the search, then the screen.

## What breaks silently

- **Theme colours are read as seldom as possible.** Every `getProperTextColor()` and its kind builds
  commons a new `BaseConfig`, and `updateTextColors` reads all three again for every layout it
  descends into. So `setupPages` reads the colours once and hands them to every page and group, the
  first page's own views colour themselves, and `updateTextColors` is handed only the rows of the
  page coming up (`pageShown`). Reading them per group and painting the first page through commons
  had `onResume` at 26ms against the cards' 14ms; it is 10ms this way.
- **The bar's fill is laid once and faded** (`makeTopBarFloating`, `updateTitleFade`).
  `AppBarLayout` wraps every colour it is given in a new drawable, which every frame of a scroll
  was paying for.
- **There are two scrollers.** Anything done to the one upstream knows, `settings_nested_scrollview`
  (insets, the bar's room, a scroll listener), wants doing to `settings_pages_scroller` too, or only
  the first page gets it; the bar follows `SettingsPages.scroller`, whichever is up.
- **A group whose every row is hidden disappears with them** (`refreshGroups`), which only happens
  after the setup functions have decided what this platform offers - so `setupPages` runs last.
- A row added to the layout wants its setup in `SettingsActivity` and nothing else, but a row added
  outside any page is never shown.
