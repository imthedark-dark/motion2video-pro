# Accepted phases and integration plan

Read-only verification before Phase 6: origin/main c8d9d19, phase-1 624e48b, phase-2 8f65384, phase-3 eb534c0, phase-4 9bc7677, phase-5 815cb4f. Each earlier phase is an ancestor of phase-5; phase-5 is 5 commits ahead / 0 behind main. `git merge-tree --write-tree origin/main origin/security/phase-5` exits 0: no textual conflict at those exact heads. feature/background-render 8270dd4 is already contained in main. No branch was merged; no main push was performed.

For only the accepted security phases, prefer one PR security/phase-5 → main, preserving the five commits with a merge commit. Sequential phase PRs are unnecessary for this stack. Re-run checks and conflict simulation after any main changes. Commands for the owner/reviewer (proposed, not executed here):

```sh
git fetch origin
git merge-base --is-ancestor origin/main origin/security/phase-5
git log --oneline origin/main..origin/security/phase-5
git merge-tree --write-tree origin/main origin/security/phase-5
gh pr create --base main --head security/phase-5 --title 'Integrate accepted security phases 1–5'
# After review and green required checks, owner approval:
gh pr merge <PR-number> --merge
```

If Phase 6 is also accepted before integration, a single PR feature/phase-6 → main includes the five security commits and the feature commits. Alternatively merge the security PR with a merge commit first, then open feature/phase-6 → main. Avoid squash/rebase merging the shared intermediate security stack if preserving ancestry matters; that can duplicate changes in the subsequent feature PR. Recheck the exact final heads; a current clean simulation cannot guarantee future conflict freedom.

Build verification is debug and **unsigned**, minified release. No production key was created or used, and no release was published. Host UI/clock/YUV/policy/security checks are documented in PHASE6_FEATURES.md; physical-device, native codec/A-V, Android quota and full OWASP-feed/private-reporting gaps remain. Do not describe these as all acceptance checks passing. Phase 7 awaits separate confirmation.
