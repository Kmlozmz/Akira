<p align="center">
  <img src=".github/assets/akira-header.svg" alt="Akira: local and online music" width="100%" />
</p>

<p align="center">A player for the music you keep and the music you have yet to discover.</p>

<p align="center">
  <a href="https://github.com/Kmlozmz/Akira/releases"><img src="https://img.shields.io/badge/version-0.3.1-FA243C?style=flat-square" alt="Version 0.3.1" /></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-242428?style=flat-square" alt="Android 8.0 or later" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-242428?style=flat-square" alt="GPL version 3" /></a>
  <img src="https://img.shields.io/badge/languages-ES%20%2F%20EN-242428?style=flat-square" alt="Spanish and English" />
</p>

<p align="center">
  <a href="https://github.com/Kmlozmz/Akira/releases"><strong>Download Akira</strong></a> ·
  <a href="#features">Features</a> ·
  <a href="#installation-and-updates">Updates</a> ·
  <a href="https://github.com/Kmlozmz/Akira/issues">Report an issue</a>
</p>

---

## About Akira

Akira is an open-source Android music player written in Kotlin. It brings together a local library, online music, synchronized lyrics and sound settings in an interface you can customize.

Listen to files already on your phone without an account or internet connection. Switch to online mode to browse recommendations, search for songs or start a radio. Linking a YouTube Music account is optional and provides access to your personal content.

Online mode is still in development. Catalog availability, account connections and some recommendations depend on external services.

## Features

<table>
  <tr>
    <td width="50%" valign="top"><h3>Your library</h3><p>Songs, albums, artists, folders and playlists. Local search, favorites and filters to exclude unwanted audio.</p></td>
    <td width="50%" valign="top"><h3>Music discovery</h3><p>Recommendations, online search, radio and a roulette to choose your next song. Guest listening is supported.</p></td>
  </tr>
  <tr>
    <td valign="top"><h3>Sound settings</h3><p>Crossfade with editable curves, volume normalization, an equalizer and profiles for different audio outputs.</p></td>
    <td valign="top"><h3>Follow the lyrics</h3><p>Multiple providers, automatic selection of synchronized lyrics and manual timing correction.</p></td>
  </tr>
  <tr>
    <td valign="top"><h3>Make it yours</h3><p>Light and dark themes, artwork colors, full-screen covers and configurable Liquid Glass controls with haptic feedback.</p></td>
    <td valign="top"><h3>Playback controls</h3><p>An editable queue, sleep timer, widgets and controls in notifications and on the lock screen.</p></td>
  </tr>
</table>

## Local and online listening

The mode selector changes the content of Home, Library and Search. Each mode retains its playback context.

| | Local mode | Online mode |
| :--- | :--- | :--- |
| **Music** | Audio files on your device. | Catalog and content available through the provider. |
| **Home** | Shortcuts and suggestions from your library. | Recommendations, radio and collections to explore. |
| **Account** | Not required. | Optional; linking YouTube Music gives access to personal content. |
| **Connection** | Library browsing and playback work offline. | Required to load and play online content. |

Guest favorites stay on your phone. When you link an account, online mode uses that account's content. Online playback does not turn songs into local files.

### Local library

- Browse songs, albums, artists, folders and playlists.
- Choose included folders and a minimum audio duration.
- Review filtered songs and restore the ones you want to keep in the library.
- Create playlists, save favorites and reorder the queue.

Filters change what Akira displays; they do not delete or move your files.

### Sound and transitions

Crossfade lets you adjust the duration and the outgoing and incoming volume curves. Select a preset or edit a curve directly.

The equalizer provides five bands and preamplification. Output profiles store different settings for speakers and headphones. Volume normalization offers track and album modes alongside original volume.

Effect and transition availability depends on the device, audio output and track format.

### Lyrics and artwork

Akira reads local lyrics or searches multiple providers. Automatic search compares available results and prioritizes synchronized lyrics. Correct timing offsets from the player when needed.

Choose framed or full-screen artwork, cover-derived colors and animated artwork when available. Configure Liquid Glass opacity and diffusion under Appearance. Haptic feedback and reduced motion have separate settings.

## Installation and updates

### First installation

1. Open [GitHub Releases](https://github.com/Kmlozmz/Akira/releases) and choose a version.
2. Download that release's **`Akira-<version>.apk`** file.
3. Open the APK and confirm installation in Android. If prompted, allow installation from the browser or file manager you are using.
4. Follow the initial setup to add your music and choose preferences.

**Requirements:** Android 8.0 or later. Public releases include only the regular Release APK.

### Updating in the app

Open **Settings → Application → Updates**.

| Step | What happens |
| :--- | :--- |
| **Check** | Akira looks for a compatible version and displays its release notes. Postpone the prompt for the session or ask to be reminded tomorrow. |
| **Download** | Start the download when you choose. View downloaded MB and progress, or cancel it. |
| **Verify** | Akira checks file size, integrity, package identifier, version and signing certificate. |
| **Install** | If permission is missing, Akira opens Android settings. Grant it and return to continue with the installer. |

Android asks you to confirm installation. A compatible update preserves your library and settings without uninstalling Akira.

Release notes use the app's selected language, or the device language when set to System, when a translation is available. English is the fallback.

## Permissions and data

Akira requests access needed by the features you use:

| Permission or service | Purpose |
| :--- | :--- |
| **Audio files** | Find and play music stored on your phone. |
| **Notifications** | Display playback controls when required by Android. |
| **App installation** | Open a downloaded update when you choose to install it from Akira. |
| **Online services** | Retrieve music, lyrics, artist information and updates from their respective providers. |

A YouTube Music account is optional. Update checks contact GitHub and do not include your music-account session.

## Frequently asked questions

<details>
<summary><strong>Do I need an account?</strong></summary>

No. The local library works without an account, and online mode supports guests. Link YouTube Music to access your personal content.

</details>

<details>
<summary><strong>Does Akira work offline?</strong></summary>

Yes, for your local library and music stored on the device. Online music, remote lyrics, artist information and updates require internet access.

</details>

<details>
<summary><strong>Why is a song missing lyrics or animated artwork?</strong></summary>

These are not available for every song. Availability depends on your local files, the providers and format compatibility.

</details>

<details>
<summary><strong>What limitations should I expect?</strong></summary>

Online mode remains in development. Catalog access, recommendations and account connections may change. Sound effects, animated content and system features can behave differently across phones.

</details>

## Reporting an issue

Open an [issue](https://github.com/Kmlozmz/Akira/issues) and include:

- Akira version, phone model and Android version.
- Local or online mode; for online mode, whether you are signed in or listening as a guest.
- Steps to reproduce, the expected result and what happened.
- A screenshot or recording when helpful.

Check attachments for passwords, cookies and personal information before sharing them. Suggestions and contributions are welcome through issues and pull requests.

## License and credits

Akira is distributed under [GNU GPL version 3](LICENSE). Third-party library, font and resource licenses are recorded in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and bundled with the app.

Akira is an independent project.
