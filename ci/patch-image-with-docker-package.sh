#!/bin/sh

set -o nounset
set -o errexit

main () {
    if [ $# -lt 3 ]; then
        echo "$0: wrong number of arguments; expected 3, got $#"
        return 1
    fi
    original_image=$1; shift
    output_image=$1; shift
    docker_package=$1; shift

    container_hash=$(docker run --detach "$original_image")
    docker exec "$container_hash" apt update
    if [ "$docker_package" = docker.io ]; then
        docker exec "$container_hash" apt install --yes docker.io
    else
        docker exec "$container_hash" apt install --yes apt-transport-https ca-certificates curl software-properties-common curl gnupg
        docker exec "$container_hash" sh -c 'curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -'
        docker exec "$container_hash" add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu    xenial    stable"
        docker exec "$container_hash" apt update
        docker exec "$container_hash" apt install --yes docker-ce
    fi
    docker commit "$container_hash" "$output_image"
    docker stop "$container_hash"
}


main "$@"
