# AppCDS capability detector

`AppCdsCapabilityDetector` is a fail-closed gate for dynamic AppCDS flags.
It does not infer support from a Java version string or vendor name.

For one exact Java executable, the detector:

1. resolves the executable to its real path and records its bounded SHA-256,
   byte length, and modification time;
2. builds a tiny bounded probe JAR from repository-owned classes;
3. launches a child JVM with the documented `-Xshare:on` and
   `-XX:ArchiveClassesAtExit=<path>` flags;
4. requires a regular, non-empty archive below the configured byte ceiling;
5. hashes that archive with a bounded streaming read;
6. launches a second child JVM with `-Xshare:on` and
   `-XX:SharedArchiveFile=<path>`; and
7. requires a successful exit, the exact probe marker, and an unchanged Java
   executable identity.

Each child has a timeout and continuously drained output capped at 64 KiB.
Temporary probe files are removed on a best-effort basis after classification.

The result status is one of `SUPPORTED`, `UNSUPPORTED`, `TIMED_OUT`, or
`ERROR`. Result instances are created only by the detector. Only `SUPPORTED`
can return archive-creation or archive-consumption arguments, and the caller
must provide the same real Java executable with the same size, modification
time, and SHA-256 that passed the probe. Every other status or executable
identity returns an empty argument list, so an unsupported or inconclusive flag
is never forwarded to a Starsector launch.

Archive argument construction also validates its path:

- creation requires an existing real parent directory and rejects an existing
  symlink or non-file target;
- consumption requires a real, non-symlink, non-empty regular file within the
  archive byte ceiling.

The focused workflow runs the production detector and a separate-JVM synthetic
caller on Linux, macOS, and Windows with Temurin 17. This proves capability
classification, exact-executable binding, and flag gating only. It does not
claim a startup-time change, and it does not yet create a Starsector application
archive.
