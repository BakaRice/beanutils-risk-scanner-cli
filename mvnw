#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
maven_version=3.9.9
distribution_dir="$script_dir/.mvn/wrapper/dists/apache-maven-$maven_version"
distribution_url=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$maven_version/apache-maven-$maven_version-bin.tar.gz

if [ ! -x "$distribution_dir/bin/mvn" ]; then
    mkdir -p "$script_dir/.mvn/wrapper/dists"
    archive_path="${TMPDIR:-/tmp}/beanutils-scanner-apache-maven-$maven_version.tar.gz"
    curl -fsSL "$distribution_url" -o "$archive_path"
    tar -xzf "$archive_path" -C "$script_dir/.mvn/wrapper/dists"
fi

exec "$distribution_dir/bin/mvn" "$@"
