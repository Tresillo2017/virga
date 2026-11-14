#!/usr/bin/env bash
# Release versioning for Virga. Used by the release workflows and runnable locally.
#
#   release-version.sh parse <tag>                    -> key=value lines for a tag such as v1.2.3 or v1.2.3-beta.2
#   release-version.sh next <bump> <channel> [tags]   -> the next tag, given existing tags (default: git tag --list 'v*')
#
# Tags are vMAJOR.MINOR.PATCH for stable releases and vMAJOR.MINOR.PATCH-(alpha|beta|rc).N for pre-releases.
#
# versionCode is derived from the version, so it always grows in release order and never depends on a CI counter:
#   MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + STAGE
#   STAGE = N for alpha.N (1-29), 30 + N for beta.N (31-59), 60 + N for rc.N (61-89), 99 for stable.
# So 1.2.3-alpha.1 < 1.2.3-beta.1 < 1.2.3-rc.1 < 1.2.3 < 1.2.4-alpha.1, as Play requires.
set -euo pipefail

readonly TAG_PATTERN='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-(alpha|beta|rc)\.([1-9][0-9]*))?$'
readonly MAX_MINOR_PATCH=99
readonly MAX_PRERELEASE_NUMBER=29
readonly MAX_MAJOR=2099

die() {
  echo "error: $*" >&2
  exit 1
}

# Prints name, channel, prerelease, number, code and base for one tag.
parse() {
  local tag="$1"
  [[ "$tag" =~ $TAG_PATTERN ]] || die "'$tag' is not a release tag. Use v1.2.3 or v1.2.3-alpha.1, -beta.1 or -rc.1"
  local major="${BASH_REMATCH[1]}" minor="${BASH_REMATCH[2]}" patch="${BASH_REMATCH[3]}"
  local channel="${BASH_REMATCH[5]:-stable}" number="${BASH_REMATCH[6]:-0}"
  ((major <= MAX_MAJOR)) || die "major version $major is above $MAX_MAJOR"
  ((minor <= MAX_MINOR_PATCH && patch <= MAX_MINOR_PATCH)) || die "minor and patch must be at most $MAX_MINOR_PATCH"
  ((number <= MAX_PRERELEASE_NUMBER)) || die "pre-release number $number is above $MAX_PRERELEASE_NUMBER"

  local stage
  case "$channel" in
    alpha) stage=$number ;;
    beta) stage=$((30 + number)) ;;
    rc) stage=$((60 + number)) ;;
    stable) stage=99 ;;
  esac
  local prerelease=false
  [[ "$channel" == stable ]] || prerelease=true

  echo "tag=$tag"
  echo "name=${tag#v}"
  echo "base=$major.$minor.$patch"
  echo "channel=$channel"
  echo "number=$number"
  echo "prerelease=$prerelease"
  echo "code=$((major * 1000000 + minor * 10000 + patch * 100 + stage))"
}

# Release tags from stdin, sorted from oldest to newest release.
sorted_tags() {
  local tag code
  while read -r tag; do
    [[ "$tag" =~ $TAG_PATTERN ]] || continue
    code=$(parse "$tag" | sed -n 's/^code=//p')
    echo "$code $tag"
  done | sort -n | awk '{print $2}'
}

# The next tag. <bump> is major, minor, patch or none; <channel> is alpha, beta, rc or stable.
#   none + stable   promotes the latest pre-release base (1.3.0-rc.2 -> 1.3.0)
#   none + rc/beta  continues the latest base in that channel (1.3.0-beta.1 -> 1.3.0-beta.2, or -> 1.3.0-rc.1)
#   patch/minor/major bumps from the latest stable release, then applies the channel.
next() {
  local bump="$1" channel="$2"
  shift 2
  local tags
  if (($#)); then tags=$(printf '%s\n' "$@"); else tags=$(git tag --list 'v*'); fi
  local ordered
  ordered=$(printf '%s\n' "$tags" | sorted_tags)

  local latest_stable="" latest=""
  latest=$(printf '%s\n' "$ordered" | tail -n 1)
  latest_stable=$(printf '%s\n' "$ordered" | grep -Ev -- '-(alpha|beta|rc)\.' | tail -n 1 || true)

  local base
  case "$bump" in
    none)
      [[ -n "$latest" ]] || die "no release yet: choose a bump for the first release"
      base=$(parse "$latest" | sed -n 's/^base=//p')
      if [[ "$latest" == "v$base" ]]; then
        die "the latest release v$base is already stable: choose a bump"
      fi
      ;;
    major | minor | patch)
      local from="${latest_stable:-v0.0.0}"
      IFS=. read -r major minor patch <<<"${from#v}"
      case "$bump" in
        major) base="$((major + 1)).0.0" ;;
        minor) base="$major.$((minor + 1)).0" ;;
        patch) base="$major.$minor.$((patch + 1))" ;;
      esac
      ;;
    *) die "bump must be major, minor, patch or none, not '$bump'" ;;
  esac

  local tag
  case "$channel" in
    stable) tag="v$base" ;;
    alpha | beta | rc)
      local last_number
      last_number=$(printf '%s\n' "$ordered" | sed -n "s/^v${base//./\\.}-$channel\.\([0-9]*\)$/\1/p" | sort -n | tail -n 1)
      tag="v$base-$channel.$((${last_number:-0} + 1))"
      ;;
    *) die "channel must be alpha, beta, rc or stable, not '$channel'" ;;
  esac

  if printf '%s\n' "$ordered" | grep -qx -- "$tag"; then die "$tag already exists"; fi
  # Refuse to go backwards, for example a beta after the same version's rc, or anything after its stable release.
  if [[ -n "$latest" ]]; then
    local new_code latest_code
    new_code=$(parse "$tag" | sed -n 's/^code=//p')
    latest_code=$(parse "$latest" | sed -n 's/^code=//p')
    ((new_code > latest_code)) || die "$tag would not be newer than $latest"
  fi
  parse "$tag" >/dev/null
  echo "$tag"
}

case "${1:-}" in
  parse) parse "${2:?tag required}" ;;
  next) shift; next "${1:?bump required}" "${2:?channel required}" "${@:3}" ;;
  *) die "usage: $0 parse <tag> | next <bump> <channel> [tags...]" ;;
esac
