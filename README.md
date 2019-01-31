# Performance Harness
## Installation

1. Clone the repository on your server and `cd` into it.
2. `cd` into `systemd` directory and run `./install`. This will install copy of
   the repository files into `/opt/rchain-perf-harness` and configuration files
   into `/etc/rchain-perf-harness`.
3. Edit the `/etc/rchain-perf-harness/drone.env` configuration file:

       DRONE_HOST

   Set this to HTTP(S) URL that'll point to this Drone instance from outside.

       DRONE_GITHUB_CLIENT
       DRONE_GITHUB_SECRET

   Set this to values of an GitHub OAuth application created at
   https://github.com/organizations/rchain/settings/applications

       DRONE_SECRET

   Set this to a random string. It's used to authenticate Drone agent with
   server. Both server and agent source this file.

       DRONE_ORGS

   Set this to comma separated list of GitHub organizations whose users can use
   this Drone instance.

       DRONE_ADMIN

   Set this to comma separated list of GitHub users who can manage this Drone
   instance.

4. Start the metrics and Drone services:

       systemctl start metrics drone

5. Edit the `/etc/rchain-perf-harness/hubot.env` configuration file:

       DRONE_SERVER
       DRONE_TOKEN

   Get these values from `${DRONE_HOST}/account/token` page.

       DRONE_BUILD_REPO

   _GitHub path_ of this repository, i.e. most probably
   `rchain/rchain-perf-harness` unless this is a fork.

       HUBOT_DISCORD_TOKEN

   Set this to token of a [Discord bot you've created](https://discordapp.com/developers/applications/).  The token is
   located under the 'Bots' tab.

   Also, you will want to add the Discord bot to your server by following the link: `https://discordapp.com/api/oauth2/authorize?client_id=<CLIENT_ID>&scope=bot&permissions=1`
   where the `<CLIENT_ID>` is copied over from `https://discordapp.com/developers/applications/` .

6. Start the Hubot:

       systemctl start hubot

Services will be started on every boot. Check with

    systemctl status rchain-perf-harness.target
    systemctl status metrics
    systemctl status drone
    systemctl status hubot


7. Set up nginx

```
# cat >/etc/nginx/sites-enabled/perf <<'EOF'
server {

    server_name drone.perf.rchain-dev.tk;

    location / {
        proxy_pass http://127.0.0.1:8080;

        proxy_set_header  Host               $host;
        proxy_set_header  X-Real-IP          $remote_addr;
        proxy_set_header  X-Forwarded-For    $proxy_add_x_forwarded_for;
        proxy_set_header  X-Forwarded-Proto  $scheme;
        client_max_body_size 50m;
    }
}

server {

    server_name grafana.perf.rchain-dev.tk;

    location / {
        proxy_pass http://127.0.0.1:13000;

        proxy_set_header  Host               $host;
        proxy_set_header  X-Real-IP          $remote_addr;
        proxy_set_header  X-Forwarded-For    $proxy_add_x_forwarded_for;
        proxy_set_header  X-Forwarded-Proto  $scheme;
    }
}
EOF
```

8. Add DNS records for `drone.perf.rchain-dev.tk` and `grafana.perf.rchain-dev.tk`
9. Set up nginx TLS certificate

Follow https://certbot.eff.org/lets-encrypt/ubuntubionic-nginx

### Renewing the TLS certificate

    $ certbox renew
