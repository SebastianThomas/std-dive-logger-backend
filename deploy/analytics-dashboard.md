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
kubectl apply -k std-dive-logger-backend/std-dive-logger-analytics/manifests/bootstrap/dev
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

## Diagnosing CI Tailnet joins

Run 34057016996 timed out in `tailscale up`, before any Kubernetes manifests were
read. The previous release's setup and deploy both joined successfully using the
same Tailscale 1.102.3 client. The manifest relocation therefore did not cause
that connection-stage failure; the precise login/control-plane failure was not
included in the old action log.

Setup, deployment and DB jobs now use separate runner hostnames, log out on exit,
and on failure report Tailscale backend state/health plus the Headscale `/health`
HTTP status. These diagnostics omit auth URLs, key values and peer listings.
A failed join uses `TS_AUTHKEY` (the runner key), not `ANALYTICS_TS_AUTHKEY` (the
persistent app node). Do not rotate the app key to fix a runner login timeout.

The subsequent diagnostics showed IPv4 HTTP 200 for both `/health` and
`/key?v=142`, while the daemon still timed out fetching the control key. An absent
IPv6 DNS record does not explain the working IPv4 request. This matches the
network-dependent ML-KEM TLS ClientHello failure reported in
[tailscale/tailscale#20777](https://github.com/tailscale/tailscale/issues/20777).
It is a working hypothesis, not yet a confirmed packet-level diagnosis here.

The three CI workflows now install a runner-local systemd drop-in setting
`GODEBUG=tlsmlkem=0` for **tailscaled**, before the shared connection action starts
it. Setting this only in the CLI step's environment would not affect the daemon.
This disables the post-quantum key-exchange option for the temporary runner's Go
TLS connections; HTTPS and certificate verification remain enabled. Persistent
app nodes and the Headscale server are unchanged. Verify a successful join in the
next workflow run; remove this compatibility setting when the underlying network
path or client compatibility issue is resolved. No secret rotation is required.
