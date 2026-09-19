# JDownloader Plugins

Custom decrypter and hoster plugins for [JDownloader 2](https://jdownloader.org/).

---

## Available Plugins

### 1. `filmo.to` Decrypter Plugin (`FilmoTo`)

- **Type**: Decrypter / Crawler (`jd.plugins.PluginForDecrypt`)
- **Supported URLs**: `https://filmo.to/movies/<slug>`
- **Source**: [`src/jd/plugins/decrypter/FilmoTo.java`](src/jd/plugins/decrypter/FilmoTo.java)
- **Precompiled**: [`dist/jd/plugins/decrypter/FilmoTo.class`](dist/jd/plugins/decrypter/FilmoTo.class)

#### How it works:
1. Matches `https://filmo.to/movies/<slug>` links added to LinkGrabber.
2. Scrapes the movie metadata, title, and CSRF token.
3. Automatically extracts all encrypted provider tokens (`data-p="..."`).
4. Performs the session-authenticated POST handshake to `/n` to mint stream tokens.
5. Resolves all external hoster mirrors (e.g. **VOE**, **Byse**) and adds them to JDownloader as a named package for immediate downloading.

---

## Installation

### Easy (Precompiled)
1. Copy the compiled `.class` file into your JDownloader installation's plugin directory:
   ```bash
   cp dist/jd/plugins/decrypter/FilmoTo.class <jdownloader_dir>/jd/plugins/decrypter/
   ```
   *(For Docker / Unraid installations, this is typically `/mnt/user/appdata/jdownloader/jd/plugins/decrypter/`)*.
2. Restart JDownloader to load the new plugin.
3. Paste any supported URL into JDownloader LinkGrabber.

---

## Building from Source

Requirements: Java 8+ JDK (compiled with `--release 8` for Java 1.8 runtime compatibility).

```bash
# Run the build script pointing to your JDownloader installation:
./scripts/build.sh /path/to/jdownloader
```

Or manually:
```bash
javac --release 8 \
  -cp "<jdownloader_dir>/Core.jar:<jdownloader_dir>/JDownloader.jar:<jdownloader_dir>/libs/*" \
  -d dist/ \
  src/jd/plugins/decrypter/FilmoTo.java
```
