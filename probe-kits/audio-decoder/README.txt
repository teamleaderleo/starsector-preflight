STARSECTOR PREFLIGHT — REAL-INSTALL AUDIO DECODER PROBE KIT

What this kit does
------------------
This is a self-contained read-only probe for macOS. The verified preflight.jar is
included in the downloaded kit. You do not need to clone the repository, install
Maven, or build anything before running the probe.

The probe records exact loaded JOrbis, Jogg, and Slick OpenAL class identities,
method descriptors, source archive hashes, and defining classloaders. It retains
the original class bytes and does not enable Ogg/Vorbis prepared-audio caching.

Run the probe
-------------
1. Unzip the kit.
2. Open the unzipped folder.
3. Double-click:

     run-audio-decoder-probe-macos.command

If macOS blocks the downloaded script, Control-click it and choose Open. You can
also open Terminal in the folder and run:

  chmod +x run-audio-decoder-probe-macos.command
  ./run-audio-decoder-probe-macos.command

The runner first checks the normal macOS Starsector application locations. If it
cannot find the application there, it opens an application picker. The runner
always changes into its own kit directory before starting Java, so a Terminal
session that begins in your home folder will not cause Preflight to scan protected
folders under ~/Library.

You can still provide the path explicitly:

  ./run-audio-decoder-probe-macos.command --game "/Applications/Starsector.app"

During the Starsector run
-------------------------
1. Reach the main menu and let its music play for about 30 seconds.
2. Load a representative save or start a campaign.
3. Trigger several UI or combat sound effects.
4. Exit Starsector normally.

After the game exits, the script automatically creates and reveals:

  audio-decoder-probe-results-YYYYMMDD-HHMMSS.zip

Upload that generated results ZIP. Do not upload this kit ZIP again.

The upload ZIP contains only the bounded JSON reports, a console log, and probe
metadata. It excludes startup.jfr, Starsector binaries, mod JARs, game assets,
and saves. The complete local run directory remains beside the script in case a
later review specifically needs the JFR recording.

Optional source checkout
------------------------
The probe does not require a source checkout. If you also want the repository in
your normal projects folder, double-click:

  setup-project-checkout-macos.command

That helper safely clones or fast-forward updates:

  ~/Projects/starsector-preflight

It refuses to overwrite a non-Git directory or a checkout with uncommitted
changes.

Kit identity
------------
SOURCE_COMMIT.txt records the exact repository commit used to build the bundled
preflight.jar. CHECKSUMS.sha256 authenticates the files in the kit.
