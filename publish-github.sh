#!/bin/bash
# Publishes the prepared repository to GitHub and creates the two releases.
# Run only after `gh auth login` has succeeded.
set -euo pipefail

export PATH="$HOME/.local/bin:$PATH"
cd "$HOME/munhana-github"

REPO_NAME="${REPO_NAME:-munhana}"
DESCRIPTION="Munhana — Arabic graphing calculator for Android. Built with AI."

if ! gh auth status >/dev/null 2>&1; then
    echo "GitHub is not authenticated. Run: gh auth login" >&2
    exit 1
fi

LOGIN=$(gh api user --jq .login)
NAME=$(gh api user --jq '.name // .login')
EMAIL=$(gh api user --jq '.email // empty')
git config user.name "$NAME"
git config user.email "${EMAIL:-$LOGIN@users.noreply.github.com}"

# Keep the commit author consistent with the GitHub account.
git commit --amend --reset-author --no-edit >/dev/null

if gh repo view "$LOGIN/$REPO_NAME" >/dev/null 2>&1; then
    echo "Repository already exists: $LOGIN/$REPO_NAME"
    git remote remove origin 2>/dev/null || true
    git remote add origin "https://github.com/$LOGIN/$REPO_NAME.git"
    git push -u origin main
else
    gh repo create "$REPO_NAME" --public --source=. --remote=origin \
        --description "$DESCRIPTION" --push
fi

gh repo edit "$LOGIN/$REPO_NAME" \
    --add-topic android,graphing-calculator,math,arabic,ocr \
    --enable-issues --enable-wiki=false >/dev/null 2>&1 || true

# Tag the current source as 1.1. The 1.0 release keeps the archived APK and
# documents that version-controlled source starts at 1.1.
git tag -f v1.1 >/dev/null
git push -f origin v1.1 >/dev/null

gh release view v1.0 >/dev/null 2>&1 || \
    gh release create v1.0 "release-assets/munhana-1.0.apk" \
        --title "منحنى 1.0 — الإصدار الأول" \
        --notes-file "$HOME/munhana-github/release-assets/notes-1.0.md" \
        --latest=false

gh release view v1.1 >/dev/null 2>&1 || \
    gh release create v1.1 "release-assets/munhana-1.1.apk" \
        --title "منحنى 1.1 — محرّر ولوحة رياضيات وتحسين OCR" \
        --notes-file "$HOME/munhana-github/release-assets/notes-1.1.md" \
        --latest

echo
echo "Published: https://github.com/$LOGIN/$REPO_NAME"
gh release list
