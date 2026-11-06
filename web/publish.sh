#!/bin/sh
# Publishes web/site (from web/build.sh) to the gh-pages branch, which GitHub
# Pages serves. Run from CI with GH_TOKEN and GITHUB_REPOSITORY set; the
# commit message names $1 (what was built).
set -e
cd "$(dirname "$0")/site"
touch .nojekyll
rm -rf .git
git init -q
git checkout -q -b gh-pages
git add -A
git -c user.name="github-actions[bot]" -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
  commit -qm "Web build: ${1:-manual}"
git push -f "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" gh-pages
rm -rf .git
