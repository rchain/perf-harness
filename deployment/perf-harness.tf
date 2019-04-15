provider "google" {
    credentials = "${file("account.json")}"
    project = "${var.project}"
    region = "${var.region}"
		version = "~> 2.3"
}

resource "google_compute_disk" "docker_cache_disk" {
	name = "docker_cache"
	type = "pd-ssd"
	size = "200"
}

resource "google_compute_instance" "perf-harness" {
	count = 1
	name = "perf-harness"
	machine_type = "n1-standard-16"
	zone = "${var.region}"

	boot_disk {
		auto_delete = true
		initialize_params {
			image = "ubuntu-1804-lts"
		}
	}

	attached_disk {
		source = "${google_compute_disk.docker_cache_disk.self_link}"
		device_name = "${google_compute_disk.docker_cache_disk.name}"
	}

	network_interface {
		network = "default"
		access_config {
		}
	}
}

resource "google_dns_record_set" "drone_cname" {
	name = "drone.${google_dns_managed_zone.dev.dns_name}"
	managed_zone = "${google_dns_managed_zone.dev.name}"
	type = "CNAME"
	ttl = 300
	rrdatas = ["${var.jump_host_dns_name}"]
}

resource "google_dns_record_set" "grafana_cname" {
	name = "grafana.${google_dns_managed_zone.dev.dns_name}"
	managed_zone = "${google_dns_managed_zone.dev.name}"
	type = "CNAME"
	ttl = 300
	rrdatas = ["${var.jump_host_dns_name}"]
}

resource "google_dns_managed_zone" "dev" {
	name = "rchain-dev"
	dns_name = "rchain-dev."
}
