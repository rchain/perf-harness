#!/bin/sh

set -o nounset
set -o errexit

main () {
    if [ $# -ne 2 ]; then
        echo "$0: wrong number of arguments; expected 2, got $#"
        return 1
    fi
    original_image=$1; shift
    output_image=$1; shift

    container_hash=$(docker run --detach "$original_image")
    docker exec "$container_hash" apt update
    docker exec "$container_hash" apt install --yes docker.io
    docker commit "$container_hash" "$output_image"
    docker stop "$container_hash"
}


main "$@"
