# admin-panel deployment — serviceAccountName patch (Phase 1b)

Companion to `k8s-rbac-serviceaccount.yaml` / `k8s-rbac-role.yaml` /
`k8s-rbac-rolebinding.yaml`. The pod resolver needs the new
`fm-admin-watcher` SA attached to the admin-panel pod so k8s auto-mounts
its token + ca.crt at `/var/run/secrets/kubernetes.io/serviceaccount/`.

**Until this patch lands, the resolver runs but the SA still has `default`
permissions — `kubectl get pods` returns 403 and the resolver degrades to
null serviceName. That's the documented graceful-degrade path; no crash.**

## Operator apply order

Apply manifests first (one cluster write, three objects):

```bash
kubectl apply -f k8s-rbac-serviceaccount.yaml
kubectl apply -f k8s-rbac-role.yaml
kubectl apply -f k8s-rbac-rolebinding.yaml
```

Then attach the SA to the deployment. Two paths — both required:

### 1. Live cluster patch (fast)

```bash
kubectl patch deployment admin-panel -n dev \
  --patch '{"spec":{"template":{"spec":{"serviceAccountName":"fm-admin-watcher"}}}}'
```

This triggers a rolling restart. The new pod mounts the new SA token; the
resolver's next `@Scheduled` tick (≤ 30 s after pod ready) populates the
index.

### 2. Source-of-truth edit (mandatory — do NOT skip)

The live patch is overwritten on the next `kubectl apply -f deployment.yaml`
from this repo. The source YAML MUST be updated in the same commit window
or the patch silently regresses next deploy.

Edit `fm-admin/deployment.yaml`, inside `spec.template.spec`, add the line
immediately above `containers:`:

```yaml
spec:                              # this is spec.template.spec
  serviceAccountName: fm-admin-watcher   # ← add this line
  containers:
    - name: admin-panel
      image: ...
```

The repo's existing `deployment.yaml` today has `spec.template.spec` on
line 16 and `containers:` on line 17 — the new line goes between them at
the same indentation as `containers:`.

## Verification (after patch + rolling restart finishes)

```bash
# Confirm the SA attached to the new pod
kubectl get pod -n dev -l app=admin-panel \
  -o jsonpath='{.items[0].spec.serviceAccountName}'
# Expected: fm-admin-watcher

# Confirm the SA can read pods (negative-control — should be empty / no error)
kubectl auth can-i list pods -n dev \
  --as=system:serviceaccount:dev:fm-admin-watcher
# Expected: yes

# Confirm the SA CANNOT do anything else (security proof)
kubectl auth can-i get secrets -n dev \
  --as=system:serviceaccount:dev:fm-admin-watcher
# Expected: no

kubectl auth can-i list pods -n default \
  --as=system:serviceaccount:dev:fm-admin-watcher
# Expected: no (namespace-scoped)

# Confirm the dashboard endpoint now reports serviceName per row
kubectl exec -n dev deploy/admin-panel -- \
  wget -qO- http://localhost/api/admin/services/db-connections \
  | head -100
# Expected: pod rows now carry "serviceName": "main" / "crypto" / etc.
# External rows still have "serviceName": null + "isExternal": true.
```

## Rollback

If anything looks wrong after the patch:

```bash
# Detach the SA (reverts to default; resolver degrades to null serviceName)
kubectl patch deployment admin-panel -n dev \
  --patch '{"spec":{"template":{"spec":{"serviceAccountName":"default"}}}}'

# Or remove the entire RBAC stack
kubectl delete rolebinding fm-admin-watcher-pods-reader -n dev
kubectl delete role pods-reader -n dev
kubectl delete serviceaccount fm-admin-watcher -n dev
```

The resolver's graceful-degrade contract means rollback never breaks the
dashboard endpoints — they revert to the Phase-1a behaviour (null
serviceName, accurate isExternal totals).
