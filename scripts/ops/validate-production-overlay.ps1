param(
    [string]$OverlayPath = "infrastructure/kubernetes/overlays/production",
    [switch]$RequireImageDigests,
    [string]$PrometheusAlertsPath = "infrastructure/kubernetes/observability/prometheus-alerts.yml",
    [string]$AlertmanagerConfigPath = "infrastructure/kubernetes/observability/alertmanager.yml",
    [string]$GrafanaDashboardPath = "infrastructure/kubernetes/observability/grafana-legent-overview.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Fail($Message) {
    Write-Error $Message
    exit 1
}

$kustomization = Join-Path $OverlayPath "kustomization.yml"
$networkPolicy = Join-Path $OverlayPath "network-policy.yml"
$externalSecrets = Join-Path $OverlayPath "external-secrets.yml"
$prometheusAlerts = $PrometheusAlertsPath

if (-not (Test-Path $kustomization)) { Fail "Missing production kustomization: $kustomization" }
if (-not (Test-Path $networkPolicy)) { Fail "Missing production network policy: $networkPolicy" }
if (-not (Test-Path $externalSecrets)) { Fail "Missing production external secrets: $externalSecrets" }

$k = Get-Content -Path $kustomization -Raw
$np = Get-Content -Path $networkPolicy -Raw
$es = Get-Content -Path $externalSecrets -Raw
$errors = New-Object System.Collections.Generic.List[string]

function ConvertTo-MarkdownAnchor([string]$Heading) {
    $anchor = $Heading.Trim().ToLowerInvariant()
    $anchor = $anchor -replace '`([^`]+)`', '$1'
    $anchor = $anchor -replace '\[([^\]]+)\]\([^)]+\)', '$1'
    $anchor = $anchor -replace '[^a-z0-9\s-]', ''
    $anchor = $anchor -replace '\s+', '-'
    $anchor = $anchor -replace '-+', '-'
    return $anchor.Trim('-')
}

function Get-MarkdownAnchors([string]$Path) {
    $anchors = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($line in (Get-Content -Path $Path)) {
        if ($line -match '^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$') {
            $anchor = ConvertTo-MarkdownAnchor $Matches[1]
            if ($anchor) { [void]$anchors.Add($anchor) }
        }
        if ($line -match '<a\s+[^>]*(?:id|name)=["'']([^"'']+)["'']') {
            [void]$anchors.Add($Matches[1])
        }
    }
    return $anchors
}

function Test-RunbookTarget([string]$RunbookUrl) {
    if ($RunbookUrl -match '^[a-z][a-z0-9+.-]*://') { return }
    $parts = $RunbookUrl -split '#', 2
    $runbookPath = $parts[0]
    if ([string]::IsNullOrWhiteSpace($runbookPath)) {
        $errors.Add("Alert runbook_url missing local path: $RunbookUrl")
        return
    }
    if ([System.IO.Path]::IsPathRooted($runbookPath)) {
        $errors.Add("Alert runbook_url must be repository-relative: $RunbookUrl")
        return
    }
    $resolvedRunbookPath = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $runbookPath))
    $repoRoot = [System.IO.Path]::GetFullPath((Get-Location).Path).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolvedRunbookPath.StartsWith($repoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        $errors.Add("Alert runbook_url escapes repository root: $RunbookUrl")
        return
    }
    if (-not (Test-Path $resolvedRunbookPath)) {
        $errors.Add("Alert runbook_url target is missing: $RunbookUrl")
        return
    }
    if ($parts.Count -eq 2 -and -not [string]::IsNullOrWhiteSpace($parts[1])) {
        $anchors = Get-MarkdownAnchors $resolvedRunbookPath
        if (-not $anchors.Contains($parts[1])) {
            $errors.Add("Alert runbook_url anchor is missing: $RunbookUrl")
        }
    }
}

function ConvertFrom-YamlScalar([string]$Value) {
    if ($null -eq $Value) { return "" }
    $trimmed = ($Value -replace '\s+#.*$', '').Trim()
    if ($trimmed.Length -ge 2) {
        if (($trimmed.StartsWith('"') -and $trimmed.EndsWith('"')) -or ($trimmed.StartsWith("'") -and $trimmed.EndsWith("'"))) {
            $trimmed = $trimmed.Substring(1, $trimmed.Length - 2)
        }
    }
    return $trimmed
}

function Get-LineIndent([string]$Line) {
    return ([regex]::Match($Line, '^\s*')).Value.Length
}

function Get-YamlTopLevelValue([string]$Document, [string]$Key) {
    foreach ($line in @($Document -split "`r?`n")) {
        if ($line -match ('^' + [regex]::Escape($Key) + ':\s*(.+?)\s*$')) {
            return ConvertFrom-YamlScalar $Matches[1]
        }
    }
    return ""
}

function Get-YamlMetadataValue([string]$Document, [string]$Key) {
    $lines = @($Document -split "`r?`n")
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -notmatch '^(\s*)metadata:\s*$') {
            continue
        }
        $metadataIndent = $Matches[1].Length
        for ($j = $i + 1; $j -lt $lines.Count; $j++) {
            $line = $lines[$j]
            if ($line -match '^\s*$') { continue }
            $indent = Get-LineIndent $line
            if ($indent -le $metadataIndent) { break }
            if ($indent -eq ($metadataIndent + 2) -and $line -match ('^\s+' + [regex]::Escape($Key) + ':\s*(.+?)\s*$')) {
                return ConvertFrom-YamlScalar $Matches[1]
            }
        }
    }
    return ""
}

function Get-RenderedKubernetesDocuments([string]$Rendered) {
    $documents = New-Object System.Collections.Generic.List[object]
    foreach ($rawDocument in [regex]::Split($Rendered, '(?m)^---\s*$')) {
        $document = $rawDocument.Trim()
        if (-not $document) { continue }
        $documents.Add([pscustomobject]@{
            Kind = Get-YamlTopLevelValue $document "kind"
            Name = Get-YamlMetadataValue $document "name"
            Namespace = Get-YamlMetadataValue $document "namespace"
            Text = $document
        })
    }
    return @($documents.ToArray())
}

function Find-RenderedObject([object[]]$RenderedObjects, [string]$Kind, [string]$Name) {
    return @($RenderedObjects | Where-Object { $_.Kind -eq $Kind -and $_.Name -eq $Name } | Select-Object -First 1)
}

function Invoke-KustomizeRender([string]$Path) {
    $kubectl = Get-Command kubectl -ErrorAction SilentlyContinue
    $kustomize = Get-Command kustomize -ErrorAction SilentlyContinue
    $command = $null
    $arguments = @()

    if ($kubectl) {
        $command = $kubectl.Source
        $arguments = @("kustomize", $Path)
    } elseif ($kustomize) {
        $command = $kustomize.Source
        $arguments = @("build", $Path)
    } else {
        $errors.Add("Production overlay rendered-object guards require kubectl kustomize or kustomize build.")
        return $null
    }

    $output = & $command @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        $outputText = (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
        $errors.Add("Production overlay Kustomize render failed: $outputText")
        return $null
    }

    return ($output | ForEach-Object { $_.ToString() }) -join "`n"
}

function Test-RenderedNamespacePosture([object[]]$RenderedObjects) {
    $namespace = Find-RenderedObject $RenderedObjects "Namespace" "legent"
    if (-not $namespace) {
        $errors.Add("Rendered production overlay is missing Namespace legent.")
    } else {
        foreach ($requiredLabel in @(
            "pod-security.kubernetes.io/enforce: restricted",
            "pod-security.kubernetes.io/audit: restricted",
            "pod-security.kubernetes.io/warn: restricted"
        )) {
            if ($namespace.Text -notmatch [regex]::Escape($requiredLabel)) {
                $errors.Add("Rendered production Namespace legent missing restricted pod-security label: $requiredLabel")
            }
        }
    }

    foreach ($object in $RenderedObjects) {
        if ($object.Namespace -and $object.Namespace -ne "legent") {
            $errors.Add("Rendered $($object.Kind)/$($object.Name) uses non-production namespace $($object.Namespace)")
        }
    }
}

function Test-RenderedNonprodResourceDeletion([object[]]$RenderedObjects) {
    $nonprodResourceNames = @(
        "legent-postgres",
        "legent-redis",
        "legent-kafka",
        "legent-minio",
        "legent-opensearch",
        "legent-clickhouse",
        "legent-mailhog"
    )
    $statefulKinds = @("Deployment", "StatefulSet", "DaemonSet", "Service", "PersistentVolumeClaim", "Job", "CronJob")
    $remaining = @($RenderedObjects | Where-Object {
        $nonprodResourceNames -contains $_.Name -and $statefulKinds -contains $_.Kind
    })
    foreach ($object in $remaining) {
        $errors.Add("Rendered production overlay still contains nonproduction base resource $($object.Kind)/$($object.Name)")
    }
}

function Test-RenderedSecretPosture([object[]]$RenderedObjects) {
    if (-not (Find-RenderedObject $RenderedObjects "ExternalSecret" "legent-secrets")) {
        $errors.Add("Rendered production overlay must contain ExternalSecret/legent-secrets.")
    }

    $secrets = @($RenderedObjects | Where-Object { $_.Kind -eq "Secret" })
    foreach ($secret in $secrets) {
        $errors.Add("Rendered production overlay contains concrete Secret/$($secret.Name); use ExternalSecret-backed material instead.")
    }
}

function Test-RenderedNetworkPolicyPosture([string]$Rendered, [object[]]$RenderedObjects) {
    foreach ($basePolicyName in @("allow-legent-egress", "allow-ingress-to-legent-apps")) {
        if (Find-RenderedObject $RenderedObjects "NetworkPolicy" $basePolicyName) {
            $errors.Add("Rendered production overlay still contains base NetworkPolicy/$basePolicyName")
        }
    }

    $defaultDeny = Find-RenderedObject $RenderedObjects "NetworkPolicy" "production-default-deny"
    if (-not $defaultDeny) {
        $errors.Add("Rendered production overlay is missing NetworkPolicy/production-default-deny.")
    } else {
        foreach ($fragment in @("podSelector: {}", "- Ingress", "- Egress")) {
            if ($defaultDeny.Text -notmatch [regex]::Escape($fragment)) {
                $errors.Add("Rendered production default-deny NetworkPolicy missing fragment: $fragment")
            }
        }
    }

    foreach ($policy in @($RenderedObjects | Where-Object { $_.Kind -eq "NetworkPolicy" })) {
        if ($policy.Text -match 'cidr:\s*(0\.0\.0\.0/0|::/0)') {
            $errors.Add("Rendered NetworkPolicy/$($policy.Name) contains broad egress CIDR $($Matches[1])")
        }
    }

    if ($Rendered -match '0\.0\.0\.0/0|::/0') {
        $errors.Add("Rendered production overlay contains broad egress CIDR.")
    }
}

function Test-RenderedServicePosture([object[]]$RenderedObjects) {
    foreach ($service in @($RenderedObjects | Where-Object { $_.Kind -eq "Service" })) {
        if ($service.Text -match '(?m)^\s*type:\s*(LoadBalancer|NodePort|ExternalName)\s*$') {
            $errors.Add("Rendered production Service/$($service.Name) exposes unsafe service type $($Matches[1])")
        }
        if ($service.Text -match '(?m)^\s*externalIPs:\s*$') {
            $errors.Add("Rendered production Service/$($service.Name) declares externalIPs.")
        }
    }
}

function Test-RenderedImagePosture([string]$Rendered, [switch]$StrictDigests) {
    $imageMatches = [regex]::Matches($Rendered, '(?m)^\s*image:\s*"?([^"\s]+)"?\s*$')
    if ($imageMatches.Count -eq 0) {
        $errors.Add("Rendered production overlay does not contain any container images.")
        return
    }

    foreach ($match in $imageMatches) {
        $image = $match.Groups[1].Value
        $lastSegment = @($image -split '/')[-1]
        if ($lastSegment -match ':latest$') {
            $errors.Add("Rendered production image must not use latest tag: $image")
        }
        if ($image -notmatch '@sha256:' -and $lastSegment -notmatch ':.+') {
            $errors.Add("Rendered production image must be tag-pinned or digest-pinned: $image")
        }
        if ($StrictDigests -and $image -notmatch '@sha256:[a-fA-F0-9]{64}$') {
            $errors.Add("Strict image digest mode requires rendered production image digest: $image")
        }
    }
}

function Get-LineIndent([string]$Line) {
    if ($Line -match '^(\s*)') { return $Matches[1].Length }
    return 0
}

function Get-NamedListItemBlocks([string]$Text, [string]$SectionName) {
    $sectionRegex = '^\s*' + [regex]::Escape($SectionName) + ':\s*$'
    $lines = $Text -split '\r?\n'
    $blocks = @()

    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -notmatch $sectionRegex) { continue }

        $sectionIndent = Get-LineIndent $lines[$i]
        $j = $i + 1
        while ($j -lt $lines.Count) {
            $line = $lines[$j]
            if ([string]::IsNullOrWhiteSpace($line)) {
                $j++
                continue
            }

            $indent = Get-LineIndent $line
            if ($indent -lt $sectionIndent) { break }
            if ($indent -eq $sectionIndent -and $line -notmatch '^\s*-\s+') { break }

            if ($line -match '^\s*-\s+') {
                $itemIndent = $indent
                $start = $j
                $j++
                while ($j -lt $lines.Count) {
                    $nextLine = $lines[$j]
                    if ([string]::IsNullOrWhiteSpace($nextLine)) {
                        $j++
                        continue
                    }
                    $nextIndent = Get-LineIndent $nextLine
                    if ($nextIndent -lt $sectionIndent) { break }
                    if ($nextIndent -eq $sectionIndent -and $nextLine -notmatch '^\s*-\s+') { break }
                    if ($nextIndent -eq $itemIndent -and $nextLine -match '^\s*-\s+') { break }
                    $j++
                }

                $end = [Math]::Max($start, $j - 1)
                $blockText = (($lines[$start..$end]) -join "`n")
                $itemName = "(unnamed)"
                foreach ($blockLine in @($lines[$start..$end])) {
                    if ((Get-LineIndent $blockLine) -eq ($itemIndent + 2) -and $blockLine -match '^\s*name:\s*"?([^"\s]+)"?\s*$') {
                        $itemName = $Matches[1]
                        break
                    }
                }
                $blocks += [pscustomobject]@{
                    Section = $SectionName
                    Name = $itemName
                    Text = $blockText
                }
                continue
            }

            $j++
        }
    }

    return @($blocks)
}

function Test-RenderedContainerPosture($Deployment, [string]$DeploymentName) {
    $containers = @(Get-NamedListItemBlocks $Deployment.Text "containers")
    $initContainers = @(Get-NamedListItemBlocks $Deployment.Text "initContainers")
    if ($containers.Count -eq 0) {
        $errors.Add("Rendered production Deployment/$DeploymentName does not contain any containers.")
    }

    foreach ($container in @($containers + $initContainers)) {
        $label = "$($container.Section)/$($container.Name)"
        foreach ($requiredFragment in @(
            "securityContext:",
            "allowPrivilegeEscalation: false",
            "capabilities:",
            "drop:",
            "- ALL",
            "resources:",
            "limits:",
            "requests:"
        )) {
            if ($container.Text -notmatch [regex]::Escape($requiredFragment)) {
                $errors.Add("Rendered production Deployment/$DeploymentName $label missing safety fragment: $requiredFragment")
            }
        }
        foreach ($unsafePattern in @(
            '(?m)^\s*allowPrivilegeEscalation:\s*true\s*$',
            '(?m)^\s*privileged:\s*true\s*$'
        )) {
            if ($container.Text -match $unsafePattern) {
                $errors.Add("Rendered production Deployment/$DeploymentName $label contains unsafe container security setting.")
            }
        }
    }
}

function Test-RenderedDeploymentPosture([object[]]$RenderedObjects) {
    $deployments = @($RenderedObjects | Where-Object { $_.Kind -eq "Deployment" })
    if ($deployments.Count -eq 0) {
        $errors.Add("Rendered production overlay does not contain any Deployment resources.")
        return
    }

    foreach ($deployment in $deployments) {
        $name = if ($deployment.Name) { $deployment.Name } else { "(unknown)" }

        $replicasMatch = [regex]::Match($deployment.Text, '(?m)^\s*replicas:\s*(\d+)\s*$')
        if (-not $replicasMatch.Success) {
            $errors.Add("Rendered production Deployment/$name is missing explicit replicas.")
        } elseif ([int]$replicasMatch.Groups[1].Value -lt 2) {
            $errors.Add("Rendered production Deployment/$name must use at least 2 replicas.")
        }

        foreach ($requiredFragment in @(
            "maxUnavailable: 0",
            "runAsNonRoot: true",
            "seccompProfile:",
            "type: RuntimeDefault"
        )) {
            if ($deployment.Text -notmatch [regex]::Escape($requiredFragment)) {
                $errors.Add("Rendered production Deployment/$name missing safety fragment: $requiredFragment")
            }
        }
        Test-RenderedContainerPosture $deployment $name

        foreach ($unsafePattern in @(
            '(?m)^\s*runAsNonRoot:\s*false\s*$',
            '(?m)^\s*hostNetwork:\s*true\s*$',
            '(?m)^\s*hostPID:\s*true\s*$',
            '(?m)^\s*hostIPC:\s*true\s*$'
        )) {
            if ($deployment.Text -match $unsafePattern) {
                $errors.Add("Rendered production Deployment/$name contains unsafe pod or container security setting.")
            }
        }
    }
}

function Test-RenderedProductionOverlay([string]$Rendered, [object[]]$RenderedObjects, [switch]$StrictDigests) {
    if ($RenderedObjects.Count -eq 0) {
        $errors.Add("Production overlay Kustomize render produced no Kubernetes objects.")
        return
    }

    Test-RenderedNamespacePosture $RenderedObjects
    Test-RenderedNonprodResourceDeletion $RenderedObjects
    Test-RenderedSecretPosture $RenderedObjects
    Test-RenderedNetworkPolicyPosture $Rendered $RenderedObjects
    Test-RenderedServicePosture $RenderedObjects
    Test-RenderedImagePosture $Rendered -StrictDigests:$StrictDigests
    Test-RenderedDeploymentPosture $RenderedObjects
}

function Get-YamlBlockValue([string]$Block, [string]$Key) {
    $match = [regex]::Match($Block, '(?m)^\s*' + [regex]::Escape($Key) + ':\s*(?<value>.+?)\s*$')
    if (-not $match.Success) { return "" }
    return ConvertFrom-YamlScalar $match.Groups["value"].Value
}

function Get-PrometheusAlerts([string]$Path) {
    $alerts = New-Object System.Collections.Generic.List[object]
    if (-not (Test-Path $Path)) {
        $errors.Add("Missing production alert rules: $Path")
        return @()
    }

    $alertRules = Get-Content -Path $Path -Raw
    foreach ($match in [regex]::Matches($alertRules, '(?ms)^\s*-\s*alert:\s*(?<name>[^\r\n]+)(?<body>.*?)(?=^\s*-\s*alert:|\z)')) {
        $body = $match.Groups["body"].Value
        $alerts.Add([pscustomobject]@{
            Name = ConvertFrom-YamlScalar $match.Groups["name"].Value
            Severity = Get-YamlBlockValue $body "severity"
            Team = Get-YamlBlockValue $body "team"
            RunbookUrl = Get-YamlBlockValue $body "runbook_url"
            DashboardUid = Get-YamlBlockValue $body "dashboard_uid"
            DashboardPanel = Get-YamlBlockValue $body "dashboard_panel"
        })
    }

    return @($alerts.ToArray())
}

function Add-GrafanaPanelTitles([object]$Panels, [System.Collections.Generic.HashSet[string]]$Titles) {
    if ($null -eq $Panels) { return }

    foreach ($panel in @($Panels)) {
        if ($null -eq $panel) { continue }

        $titleProperty = $panel.PSObject.Properties["title"]
        if ($titleProperty -and -not [string]::IsNullOrWhiteSpace([string]$titleProperty.Value)) {
            [void]$Titles.Add([string]$titleProperty.Value)
        }

        $nestedPanelsProperty = $panel.PSObject.Properties["panels"]
        if ($nestedPanelsProperty) {
            Add-GrafanaPanelTitles $nestedPanelsProperty.Value $Titles
        }
    }
}

function Get-GrafanaDashboardHandoff([string]$Path) {
    $panelTitles = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    if (-not (Test-Path $Path)) {
        $errors.Add("Missing Grafana handoff dashboard: $Path")
        return [pscustomobject]@{ Uid = ""; PanelTitles = $panelTitles }
    }

    try {
        $dashboard = Get-Content -Path $Path -Raw | ConvertFrom-Json
    } catch {
        $errors.Add("Grafana handoff dashboard is not valid JSON: $Path")
        return [pscustomobject]@{ Uid = ""; PanelTitles = $panelTitles }
    }

    $uid = ""
    $uidProperty = $dashboard.PSObject.Properties["uid"]
    if ($uidProperty) { $uid = [string]$uidProperty.Value }
    if ([string]::IsNullOrWhiteSpace($uid)) {
        $errors.Add("Grafana handoff dashboard is missing stable uid: $Path")
    }

    $panelsProperty = $dashboard.PSObject.Properties["panels"]
    if ($panelsProperty) {
        Add-GrafanaPanelTitles $panelsProperty.Value $panelTitles
    }
    if ($panelTitles.Count -eq 0) {
        $errors.Add("Grafana handoff dashboard contains no titled panels: $Path")
    }

    return [pscustomobject]@{ Uid = $uid; PanelTitles = $panelTitles }
}

function Get-ExternalSecretKeys([string]$ExternalSecretsSource) {
    $keys = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($match in [regex]::Matches($ExternalSecretsSource, '(?m)^\s*-\s*secretKey:\s*(?<key>.+?)\s*$')) {
        $key = ConvertFrom-YamlScalar $match.Groups["key"].Value
        if (-not [string]::IsNullOrWhiteSpace($key)) {
            [void]$keys.Add($key)
        }
    }
    return $keys
}

function Test-AlertmanagerRouting([string]$Path, [object[]]$Alerts, [System.Collections.Generic.HashSet[string]]$ExternalSecretKeys) {
    if (-not (Test-Path $Path)) {
        $errors.Add("Missing Alertmanager routing config: $Path")
        return
    }

    $source = Get-Content -Path $Path -Raw
    $requiredGroupLabels = @("alertname", "severity", "team")
    $groupByMatch = [regex]::Match($source, '(?m)^\s*group_by:\s*\[(?<items>[^\]]+)\]\s*$')
    if (-not $groupByMatch.Success) {
        $errors.Add("Alertmanager routing must group alerts by alertname, severity, and team.")
    } else {
        $groupByItems = $groupByMatch.Groups["items"].Value
        foreach ($label in $requiredGroupLabels) {
            if ($groupByItems -notmatch ('["'']?' + [regex]::Escape($label) + '["'']?')) {
                $errors.Add("Alertmanager group_by is missing $label.")
            }
        }
    }

    $receiverNames = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($match in [regex]::Matches($source, '(?m)^\s*-\s*name:\s*(?<name>.+?)\s*$')) {
        [void]$receiverNames.Add((ConvertFrom-YamlScalar $match.Groups["name"].Value))
    }
    if ($receiverNames.Count -eq 0) {
        $errors.Add("Alertmanager routing config contains no receivers.")
    }

    foreach ($match in [regex]::Matches($source, '(?m)^\s*receiver:\s*(?<receiver>.+?)\s*$')) {
        $receiver = ConvertFrom-YamlScalar $match.Groups["receiver"].Value
        if (-not $receiverNames.Contains($receiver)) {
            $errors.Add("Alertmanager route references missing receiver: $receiver")
        }
    }

    $webhookUrls = @([regex]::Matches($source, '(?m)^\s*(?:-\s*)?url:\s*(?<url>.+?)\s*$'))
    if ($webhookUrls.Count -lt $receiverNames.Count) {
        $errors.Add("Alertmanager receivers must have webhook handoff URLs.")
    }
    $alertmanagerPlaceholders = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($match in $webhookUrls) {
        $url = ConvertFrom-YamlScalar $match.Groups["url"].Value
        if ($url -notmatch '^\$\{(?<name>ALERTMANAGER_[A-Z0-9_]+)\}$') {
            $errors.Add("Alertmanager webhook URL must use an ALERTMANAGER_* environment placeholder, not a literal target: $url")
        } else {
            [void]$alertmanagerPlaceholders.Add($Matches["name"])
        }
    }
    foreach ($placeholder in $alertmanagerPlaceholders) {
        if (-not $ExternalSecretKeys.Contains($placeholder)) {
            $errors.Add("Alertmanager placeholder $placeholder is not backed by production ExternalSecret data.")
        }
    }

    $sendResolvedCount = [regex]::Matches($source, '(?m)^\s*send_resolved:\s*true\s*$').Count
    if ($sendResolvedCount -lt $receiverNames.Count) {
        $errors.Add("Alertmanager receivers must set send_resolved: true.")
    }

    $teams = @($Alerts | Where-Object { -not [string]::IsNullOrWhiteSpace($_.Team) } | Select-Object -ExpandProperty Team -Unique)
    foreach ($team in $teams) {
        $teamMatcher = '(?m)^\s*-\s*team\s*=\s*["'']' + [regex]::Escape($team) + '["'']\s*$'
        if ($source -notmatch $teamMatcher) {
            $errors.Add("Alertmanager routing is missing explicit route matcher for team=$team.")
        }
    }

    $hasCriticalAlerts = @($Alerts | Where-Object { $_.Severity -eq "critical" }).Count -gt 0
    if ($hasCriticalAlerts -and $source -notmatch '(?m)^\s*-\s*severity\s*=\s*["'']critical["'']\s*$') {
        $errors.Add("Alertmanager routing is missing explicit critical severity matcher.")
    }
}

function Test-ObservabilityHandoff([string]$PrometheusPath, [string]$AlertmanagerPath, [string]$GrafanaPath, [System.Collections.Generic.HashSet[string]]$ExternalSecretKeys) {
    $alerts = @(Get-PrometheusAlerts $PrometheusPath)
    if ($alerts.Count -eq 0) {
        $errors.Add("Prometheus alert rules contain no alerts: $PrometheusPath")
        return
    }

    $dashboard = Get-GrafanaDashboardHandoff $GrafanaPath
    $validSeverities = @("warning", "critical")
    foreach ($alert in $alerts) {
        if ([string]::IsNullOrWhiteSpace($alert.Severity)) {
            $errors.Add("Alert $($alert.Name) is missing severity label.")
        } elseif ($validSeverities -notcontains $alert.Severity) {
            $errors.Add("Alert $($alert.Name) has unsupported severity '$($alert.Severity)'.")
        }

        if ([string]::IsNullOrWhiteSpace($alert.Team)) {
            $errors.Add("Alert $($alert.Name) is missing team label for Alertmanager routing.")
        }

        if ([string]::IsNullOrWhiteSpace($alert.RunbookUrl)) {
            $errors.Add("Alert $($alert.Name) is missing runbook_url.")
        } else {
            Test-RunbookTarget $alert.RunbookUrl
        }

        if ([string]::IsNullOrWhiteSpace($alert.DashboardUid)) {
            $errors.Add("Alert $($alert.Name) is missing dashboard_uid annotation.")
        } elseif ($alert.DashboardUid -ne $dashboard.Uid) {
            $errors.Add("Alert $($alert.Name) references dashboard_uid $($alert.DashboardUid), but Grafana dashboard uid is $($dashboard.Uid).")
        }

        if ([string]::IsNullOrWhiteSpace($alert.DashboardPanel)) {
            $errors.Add("Alert $($alert.Name) is missing dashboard_panel annotation.")
        } elseif (-not $dashboard.PanelTitles.Contains($alert.DashboardPanel)) {
            $errors.Add("Alert $($alert.Name) references missing Grafana panel '$($alert.DashboardPanel)'.")
        }
    }

    Test-AlertmanagerRouting $AlertmanagerPath $alerts $ExternalSecretKeys
}

foreach ($required in @(
    "external-secrets.yml",
    "network-policy.yml",
    "delete-base-secret.yml",
    "delete-nonprod-stateful.yml",
    "delete-base-egress-policy.yml",
    "deployment-security-patch.yml",
    "deployment-rollout-patch.yml"
)) {
    if ($k -notmatch [regex]::Escape($required)) {
        $errors.Add("Production kustomization missing $required")
    }
}

if ($np -notmatch "production-default-deny") {
    $errors.Add("Production default-deny NetworkPolicy is missing")
}
if ($np -match "0\.0\.0\.0/0") {
    $errors.Add("Production network policy contains broad 0.0.0.0/0 egress")
}
if ($RequireImageDigests -and $k -notmatch "digest:\s*sha256:[a-fA-F0-9]{64}") {
    $errors.Add("Strict image digest mode requires sha256 digests in production kustomization")
}
if ($RequireImageDigests) {
    $currentImage = $null
    $imageDigests = @{}
    foreach ($line in (Get-Content -Path $kustomization)) {
        if ($line -match '^\s*-\s+name:\s*"?([^"\s]+)"?\s*$') {
            $currentImage = $Matches[1]
            $imageDigests[$currentImage] = $null
            continue
        }
        if ($currentImage -and $line -match '^\s*digest:\s*"?([^"\s]+)"?\s*$') {
            $imageDigests[$currentImage] = $Matches[1]
            continue
        }
    }
    foreach ($imageName in $imageDigests.Keys) {
        if ([string]$imageDigests[$imageName] -notmatch '^sha256:[a-fA-F0-9]{64}$') {
            $errors.Add("Production image $imageName must be digest-pinned in strict mode")
        }
    }
}
if (-not $RequireImageDigests -and $k -notmatch 'newTag:\s*"1\.0\.2"') {
    $errors.Add("Production images should currently be pinned to release tag 1.0.2 or a reviewed digest update")
}
if ($es -match "replace_with|changeme|minioadmin|password") {
    Write-Warning "External secret template contains placeholder-like text; verify this is not rendered as a real Secret."
}
if ($k -match "postgres\.yml|redis\.yml|kafka\.yml|minio\.yml|opensearch\.yml|clickhouse\.yml|mailhog\.yml") {
    $errors.Add("Production overlay appears to include local stateful resources directly")
}

$renderedOverlay = Invoke-KustomizeRender $OverlayPath
if ($renderedOverlay) {
    $renderedObjects = Get-RenderedKubernetesDocuments $renderedOverlay
    Test-RenderedProductionOverlay $renderedOverlay $renderedObjects -StrictDigests:$RequireImageDigests
}

$externalSecretKeys = Get-ExternalSecretKeys $es
Test-ObservabilityHandoff $prometheusAlerts $AlertmanagerConfigPath $GrafanaDashboardPath $externalSecretKeys

if ($errors.Count -gt 0) {
    foreach ($errorMessage in $errors) {
        Write-Host "ERROR: $errorMessage" -ForegroundColor Red
    }
    exit 1
}

Write-Host "Production overlay validation passed."
