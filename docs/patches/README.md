# PKB #74 architecture companion

`pkb-74-domain-event-recovery.patch` is a reviewable, unapplied patch for
`Bio-Compass/bio-compass-architecture-docs` based on `a8d945ca33a368ac999a8a99465749fc248c56ec`.
It adds an explicitly **proposed** recovery-path decision and navigation, plus
release metadata. No central repository changes have been committed or published.

Validate with `git apply --check <patch-path>` in a central-docs checkout at that
base. Rebase release metadata against current central main before applying;
concurrent architecture PR #12 also proposes release 1.15.0. This patch must not
overwrite that proposal or silently accept either decision. Central review and
publication remain separate from PKB implementation and deployment approvals.
