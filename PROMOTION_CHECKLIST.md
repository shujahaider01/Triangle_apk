# Promoting a feature from `test` to `main` (prod)

A short, practical checklist for the one recurring decision this whole PROD/TEST
setup exists to support: "is this specific thing ready to go live?" Nothing here
is enforced by tooling — it's a habit, meant to take a couple of minutes per
promotion, not a process.

## Before you promote anything

- [ ] **It's been running on `test` (the sandbox app, `com.traineexp.app`) for a
      real stretch of time** with no bugs surfacing — a few days of your own
      actual use is a reasonable bar for most changes; longer for anything
      touching money/points/data integrity (task completion, XP calculation,
      shared-task logic).
- [ ] **You know exactly which commit(s)** on `test` this feature lives in.
      `git log --oneline` on the `test` branch, find the range, note the
      hashes. If a feature is spread across many small commits, it's fine —
      cherry-pick all of them, in order.
- [ ] **Check what else landed nearby.** Because `script.js` is one large
      file, an unrelated fix sometimes sits in the same commit as the feature
      you actually want. Skim the diff (`git show <hash>`) before picking —
      if two unrelated things got bundled into one commit, you may need to
      cherry-pick with `--no-commit` and manually stage only the hunks you
      want (`git checkout -p`), rather than the whole commit.

## The actual promotion

```
git checkout main
git cherry-pick <hash1> <hash2> ...   # in the order they were made on test
```

- [ ] If a conflict shows up, resolve it by hand, then `git add <file>` and
      `git cherry-pick --continue`. Never `git cherry-pick --abort` and give
      up without noting why — if it conflicted, `main` has probably diverged
      in that area and it's worth understanding why before forcing it through.
- [ ] **Never cherry-pick a commit that touches `config.js` or
      `config.prod.js` / anything under `app/src/*/assets/config.js`.**
      Those files are deliberately different between environments on purpose
      — a cherry-pick that overwrites `prod`'s config with `sandbox`'s values
      (or vice versa) silently repoints prod at the wrong Firebase project.
      If a real commit's diff includes both a feature change AND an
      unrelated config edit, split them and only take the feature part.

## Data shape changes

- [ ] If the feature adds a new field or top-level structure to `db`, confirm
      `patchDB()` already defaults it for orgs that don't have it yet (this is
      the existing pattern — new features should keep following it). If it
      doesn't yet, add the default as part of this same promotion, not after.
- [ ] If it's a genuinely new top-level collection (rare), remember: several
      collections are GLOBAL across the whole Firebase project, not per-org
      (`users`, `orgUsernames`, `emailOtps`, `deviceTokens`, `pushQueue`,
      `orgIconCycle`) — double check whether the new collection should be one
      of these or org-scoped under `organizations/{orgId}/data`, since that
      decision is hard to reverse later.

## After promoting

- [ ] Build and run `prodDebug` locally once, confirm the app still starts
      and the specific feature works pointed at the real `xtrainee` project
      (not just that it compiled).
- [ ] Note it somewhere (even just a one-line comment in your own memory/notes)
      — which commit(s), what feature, what date. Doesn't need to be fancy;
      just needs to exist outside your head, so six months from now you (or
      anyone else) can answer "when did X reach prod?"
- [ ] Only then is it safe to actually build/distribute a real `prodRelease`
      build with this change in it, if that's the next step.

## Rule of thumb

If you're ever unsure whether something's ready: it isn't. `main` moving
slower than `test` is the entire point of this setup — there's no cost to
waiting one more day before cherry-picking, and real cost to promoting
something that turns out to be half-baked once it's touching real trainee
data with no safety net.
