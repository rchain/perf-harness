# Performance Harness

<img src="doc/topology.png"/>

## Deployment

```
$ cd deployment
```

1. Fill out Terraform variable values in `terraform.tfvars`.
2. Execute commands

```
$ terraform plan
$ terraform apply
```

3. Execute the Ansible playbook:

```
$ ansible-playbook --inventory=<HOST>, --user=<USER> --private-key=<SSH_PRIVATE_KEY_PATH> --ask-vault-pass site.yml
```

Side note: You may also want to pass `--extra-vars "checkout_commit=<BRANCH>"`
to test out a PR against the Ansible playbook itself.

4. Configure Grafana password by opening up the its UI and logging in as
   admin.
