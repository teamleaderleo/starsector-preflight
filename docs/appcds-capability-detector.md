# AppCDS capability detector

`AppCdsCapabilityDetector` is a fail-closed gate for dynamic AppCDS flags.
It does not infer support from a Java version string or vendor name.

For one exact Java executable, the detector:

1. builds a tiny bounded probe JAR from repository-owned classes;
2. launches a child JVM with the documented `-Xshare:on` and
   `-XX:ArchiveClassesAtExit=<path>` flags;
3. requires a regular, non-empty archive below the configured byte ceiling;
4. hashes that archive with a bounded streaming read;
5. launches a second child JVM with `-Xshare:on` and
   `-XX:SharedArchiveFile=<path>`; and
6. requires a successful exit and the exact probe marker.

Each child has a timeout and continuously drained output capped at 64 KiB.
Temporary probe files are removed on a best-effort basis after classification.

The result status is one of `SUPPORTED`, `UNSUPPORTED`, `TIMED_OUT`, or
`ERROR`. Only `SUPPORTED` can return archive-creation or archive-consumption
arguments. Every other status returns an empty argument list, so an unsupported
or inconclusive flag is never forwarded to a Starsector launch.

Archive argument construction also validates its path:

- creation requires an existing real parent directory and rejects an existing
  symlink or non-file target;
- consumption requires a real, non-symlink, non-empty regular file within the
  archive byte ceiling.

The focused workflow runs the production detector and a separate-JVM synthetic
caller on Linux, macOS, and Windows with Temurin 17. This proves capability
classification and flag gating only. It does not claim a startup-time change,
and it does not yet create a Starsector application archive.
