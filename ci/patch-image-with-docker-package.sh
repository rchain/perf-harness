#!/bin/sh

set -o nounset
set -o errexit


stop_container () {
    [ -n "$container_hash" ] && docker stop "$container_hash"
}


main () {
    if [ $# -lt 2 ]; then
        echo "$0: wrong number of arguments; expected 3, got $#"
        return 1
    fi
    original_image=$1; shift
    output_image=$1; shift

    trap stop_container EXIT
    container_hash=$(docker run --detach "$original_image")

    docker exec "$container_hash" apt update
    docker exec "$container_hash" apt install --yes apt-transport-https ca-certificates curl software-properties-common curl gnupg
    docker exec "$container_hash" sh -c 'curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -'
    docker exec "$container_hash" add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu    xenial    stable"
    docker exec "$container_hash" apt update
    docker exec "$container_hash" apt install --yes docker-ce
    docker commit "$container_hash" "$output_image"
}


main "$@"
