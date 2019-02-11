#!/bin/bash -ue


main () {
    if [[ $# -ne 2 ]]; then
        echo "$0: wrong number of arguments; expected 2, got $#"
        return 2
    fi
    local container_name_substring=$1; shift
    local log_message=$1; shift

    local container_hash=$(docker ps --filter "name=$container_name_substring" --quiet | head -1)

    if [[ -z $container_hash ]]; then
        echo "$container_name_substring: container not found"
        return 3
    fi

    if [[ $container_hash == *\ * ]]; then
        echo "$container_name_substring: multiple container match the name"
        return 4
    fi

    docker logs "$container_hash" | grep "$log_message"
}


main "$@"
