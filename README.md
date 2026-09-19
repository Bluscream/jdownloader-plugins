# JDownloader Plugins

Custom decrypter and hoster plugins for [JDownloader 2](https://jdownloader.org/).

---

## Available Plugins

### 1. `filmo.to` Decrypter Plugin (`FilmoTo`)

- **Type**: Decrypter / Crawler (`jd.plugins.PluginForDecrypt`)
- **Supported URLs**: `https://filmo.to/movies/<slug>`
- **Source**: [`src/jd/plugins/decrypter/FilmoTo.java`](src/jd/plugins/decrypter/FilmoTo.java)
- **Download**: Available in [Releases](https://github.com/Bluscream/jdownloader-plugins/releases)

#### How it works:
1. Matches `https://filmo.to/movies/<slug>` links added to LinkGrabber.
2. Scrapes the movie metadata, title, and CSRF token.
3. Automatically extracts all encrypted provider tokens (`data-p="..."`).
4. Performs the session-authenticated POST handshake to `/n` to mint stream tokens.
5. Resolves all external hoster mirrors (e.g. **VOE**, **Byse**) and adds them to JDownloader as a named package for immediate downloading.

---

### 2. Universal `Jellyfin` Hoster Plugin (`JellyfinDirectDownload`)

- **Type**: Hoster Plugin (`jd.plugins.PluginForHost`)
- **Target Systems**: Any Jellyfin or Emby instance (e.g. `nginxipv6test.b-cdn.net`, `cdn.clawsucht.eu`, etc.)
- **Supported URLs**: `https?://<domain>/Items/<itemId>/Download(?:\?.*)?`
- **Source**: [`src/jd/plugins/hoster/JellyfinDirectDownload.java`](src/jd/plugins/hoster/JellyfinDirectDownload.java)
- **Settings UI** (under **Settings → Plugins → jellyfin**):
  - **`Default API Key`**: Applied to any URL missing `?api_key=` if no domain rule matches.
  - **`Default User-Agent`**: Default User-Agent header (default: `VRChat`).
  - **`Per-Domain Rules` (Multi-line editor)**: Configure API keys and custom User-Agents per server/domain:
    ```text
    # Format: domain|apiKey|userAgent
    nginxipv6test.b-cdn.net|20121df9784646bb850a06edf402e3a0|VRChat
    cdn.clawsucht.eu||VRChat
    my-home-jellyfin.net|secret_api_key|MyCustomUA
    ```
- **Features**:
  - Automatically matches the incoming URL domain against your per-domain rules to inject the correct API key and User-Agent.
  - Resolves filenames and verified file sizes from `Content-Disposition` and `Content-Range`.
  - Gives clear user guidance if an API key is missing or expired (401/403).

---

### 3. `cdn.woweepaw.de` Hoster Plugin (`CdnWoweepawDe`)

- **Type**: Hoster Plugin (`jd.plugins.PluginForHost`)
- **Target Worlds**: VRChat **Jellybean** / **Jellybean Movies** (`wrld_275e81ec-c987-40e8-ba07-565a23129e20`)
- **Supported URLs**: `https://cdn.woweepaw.de/pawlib/?action=stream&id=<hex>`
- **Source**: [`src/jd/plugins/hoster/CdnWoweepawDe.java`](src/jd/plugins/hoster/CdnWoweepawDe.java)
- **Settings UI**:
  - `User-Agent`: Configurable in **Settings → Plugins → cdn.woweepaw.de** (defaults to `VRChat` to bypass the `204 No Content` filter without manual header tweaks).
- **Features**:
  - Automatically queries media info with byte-range requests.
  - Resolves real media filenames from `Content-Disposition` (e.g. `"Star Wars - Die Rache der Sith (2005).mp4"`).
  - Retrieves exact file size from `Content-Range`.

---

## Installation

1. Download the precompiled classes or `.zip` from the [Latest Release](https://github.com/Bluscream/jdownloader-plugins/releases/latest).
2. Copy the `.class` files into your JDownloader installation's plugin directory:
   - Decrypters go to `<jdownloader_dir>/jd/plugins/decrypter/`
   - Hosters go to `<jdownloader_dir>/jd/plugins/hoster/`
   *(For Docker / Unraid installations, this is typically `/mnt/user/appdata/jdownloader/jd/plugins/`)*.
3. Restart JDownloader to load the new plugins.

---

## Building from Source

Requirements: Java 8+ JDK (compiled with `--release 8` for Java 1.8 runtime compatibility).

```bash
# Run the build script pointing to your JDownloader installation:
./scripts/build.sh /path/to/jdownloader
```
