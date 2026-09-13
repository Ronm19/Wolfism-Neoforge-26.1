# Publishing Wolfism 1.0.0

The release is prepared for free distribution without monetization. Nothing has
been uploaded or published by this release-preparation work.

## Licenses and attribution

Ronm19's original Wolfism work is MIT-licensed. Third-party libraries and recorded
audio retain their own licenses. Keep LICENSE, WOLFISM_AUDIO.md, and every bundled
META-INF license, notice, provenance record and required source ZIP intact.

Photon permits the unchanged library to be included using Jar-in-Jar when the
containing mod is not sold separately or directly monetized. This release keeps
the author's existing binary, credit and full license; it does not create a Photon
port or relicense Photon under MIT. The exact bundled terms are available at
[the release commit](https://github.com/Low-Drag-MC/Photon/blob/8ceb79e680ba977f9b38cfa660b9053d073935fa/LICENSE).

Keep CurseForge rewards and other release monetization disabled for this chosen
distribution route. If monetization is wanted later, clarify the particular
arrangement or obtain the author's written permission first. The license does not
unambiguously classify all voluntary donations or rewards for ordinary bundling;
its categorical donation prohibition applies specifically to third-party ports.
[CurseForge rewards FAQ](https://support.curseforge.com/support/solutions/articles/9000197902-reward-program-faq).

## Source publication

Use a clean export of the current maintained working tree for a new public
repository. Do not push the existing local Git history as the cleaned source:
an older commit still contains the twelve excluded legacy recordings. No history
was rewritten, and a local remote-tracking reference does not establish whether
those files are currently public on a remote server.

The prepared source ZIP omits Git history, IDE settings, build/run folders,
caches, private backups and those twelve recordings. It retains the Gradle wrapper,
current source, generated resources, tools, documentation, all 480 active clips,
and required third-party source/license archives. It is built from the current
working tree, including maintained files that were not in the old Git index.

Do not upload old release JARs, old project ZIPs or private backups as part of the
cleaned release; those historical copies still contain the excluded recordings.

## Verification scope

Use the replacement artifact's SHA-256 and its own release results. Earlier logs
belong to the earlier artifact even when the public version number is the same.
The archive audit alone is not a runtime test. Preserve the separately documented
Jade server errors, Photon distribution restrictions and runtime test limitations
in the release report; none are removed by this audio-only cleanup.
