#!/usr/bin/env sh
set -eu

file=$1
shift

directory=$(dirname "$file")
while [ "$directory" != / ] && [ ! -f "$directory/gradlew" ]; do
  directory=$(dirname "$directory")
done

if [ ! -f "$directory/gradlew" ]; then
  echo "No Gradle build holds $file. Open a file inside a sample or a workshop." >&2
  exit 1
fi

cd "$directory"
exec ./gradlew "$@"
