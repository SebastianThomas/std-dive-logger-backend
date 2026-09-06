# Private analytics dashboard

The analytics app serves `/ops`: seven workflows, queued/running/completed jobs,
next scheduled times, durations, failure types, and manual submission. The page
refreshes every five seconds and links to the existing Grafana dashboard.
History retains 30 days; the page shows the latest 100 runs. Schedules use UTC;
the browser displays times in its local zone.

Manual and scheduled work share a durable PostgreSQL queue. A partial unique
index prevents duplicate active jobs; a session advisory lock serializes workers
across replicas. After a worker stops, its unfinished run is marked interrupted
by the next worker. It is not automatically replayed: use Run now or wait for
the next scheduled submission. Batch completion can still include individual
warnings from existing workflows; inspect Grafana for those details.

## Automated dev setup

The `Analytics setup (dev)` GitHub Action runs automatically before each accepted
**dev** deployment, using the same manifest ref as the release. It can also run
manually from Actions, with `main` or another ref containing these files. Failed
setup blocks the dev deployment. It does not provision prod.

Each run updates `analytics-tailnet-authkey`, applies the certificate and waits
up to five minutes for TLS readiness. It preserves the PVC and registered node
identity. The existing `letsencrypt-prod` ClusterIssuer performs the DNS challenge
for `*.ts.homelab.sthomas.ch`; its name is the issuer name, not a prod deployment.
No DNS-provider credentials belong in this application's GitHub secrets.

### One administrator step

The existing `ci/app-deployer` service account cannot grant itself certificate
permissions. Once, using an administrator kubeconfig (the dev namespace must
already exist), run from the repository root:

```sh
kubectl apply -f deploy/bootstrap/analytics-dev-rbac.yaml
```

This grants certificate management only in `std-dive-logger-dev`; it grants no
ClusterIssuer or prod permissions. No administrator token is stored in GitHub.
Keep this RBAC outside the regular deployment overlay.

### GitHub environment: dev

Under **Settings → Environments → dev → Environment secrets**, configure:

| Secret | Value |
| --- | --- |
| `ANALYTICS_TS_AUTHKEY` | **New:** dedicated reusable, non-ephemeral Headscale pre-auth key for the persistent analytics node, with the intended Tailnet ACL tags. Keep separate from the CI runner key. |
| `TS_AUTHKEY` | Existing Headscale key used by temporary GitHub runners. |
| `HEADSCALE_URL` | Existing `https://headscale.homelab.sthomas.ch`. |
| `KUBE_API` | Existing Kubernetes API endpoint reachable over the Tailnet. |
| `KUBE_CA` | Existing base64-encoded Kubernetes CA certificate. |
| `KUBE_TOKEN` | Existing `ci/app-deployer` token; unchanged after the RBAC grant. |

Existing repository/organization secrets can remain inherited if already available
to the dev jobs; there is no need to duplicate those values. **No new GitHub
variables are required.** The hostname is already defined in `deploy/dev` as
`std-dive-analytics-dev`. All existing application deployment secrets stay as-is.
Do not add any prod secrets or variables for this setup.

The pre-auth key is needed for initial registration or replacement of the node's
persisted identity. Rotate the GitHub secret before using an expired/revoked key
for a new registration; setup only stores the key and does not validate it against
Headscale. Never delete the PVC as part of routine deployment.

After the administrator grant and secret configuration, run **Analytics setup
(dev)** once or let the next dev deployment call it. The workflow files must first
be committed/pushed (and included in the release ref used for deployment).

Dev URL after deployment:
`https://std-dive-analytics-dev.ts.homelab.sthomas.ch/ops`

This follows homelab-infra's Grafana pattern: a dedicated Tailscale node with
TLS inside the pod, no analytics Service, HTTPRoute or host port. The application
listener binds to loopback; its management listener remains available for pod
probes and metrics. Network access is the authorization boundary: permitted
Tailnet clients can start workflows, including reminder delivery. CSRF tokens
protect manual submissions. Restrict node access with the Headscale ACL.

Verify the private URL from an authorized Tailnet client and confirm there is
no public route. Check the Tailscale sidecar registration if DNS is unavailable.
After certificate renewal, restart the analytics Deployment so nginx reloads
the renewed certificate.
