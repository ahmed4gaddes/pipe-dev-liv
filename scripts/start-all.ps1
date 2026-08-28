<#
.SYNOPSIS
    Demarre toute la stack BIAT Flow en une commande, dans le bon ordre.

.DESCRIPTION
    Enchaine, en verifiant chaque etape avant de passer a la suivante :
      1. alignement de EUREKA_INSTANCE_HOSTNAME sur l'IP LAN courante (voir sync-lan-ip.ps1)
      2. infrastructure Docker (Postgres, Keycloak, RabbitMQ, ngrok, SonarQube, Zipkin)
      3. discovery-server et api-gateway, qui tournent hors Docker (voir README)
      4. les 5 microservices metier (Docker)
      5. le runner GitHub Actions self-hosted

    Le script est idempotent : ce qui tourne deja est laisse en place. Il peut donc etre
    relance sans risque apres un demarrage partiel.

.PARAMETER SkipNative
    Ne demarre pas discovery-server / api-gateway. A utiliser si vous les lancez depuis
    IntelliJ : le script verifiera quand meme qu'ils repondent.

.PARAMETER SkipRunner
    Ne demarre pas le runner GitHub Actions (inutile si vous ne declenchez aucun deploiement).

.EXAMPLE
    .\scripts\start-all.ps1
    .\scripts\start-all.ps1 -SkipNative
#>
[CmdletBinding()]
param(
    [switch]$SkipNative,
    [switch]$SkipRunner
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runnerDir = 'C:\actions-runner'

function Write-Step($n, $text) { Write-Host "`n[$n] $text" -ForegroundColor Cyan }
function Write-Ok($text)       { Write-Host "    OK   $text" -ForegroundColor Green }
function Write-Info($text)     { Write-Host "         $text" -ForegroundColor DarkGray }
function Write-Warn2($text)    { Write-Host "    !    $text" -ForegroundColor Yellow }

# Attend qu'une URL reponde. Renvoie $true/$false plutot que de lever : le script continue et
# fait un bilan final, plus utile qu'un arret sec au milieu du demarrage.
function Wait-Url($url, $timeoutSec, $label) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 | Out-Null
            return $true
        } catch {
            # Keycloak repond 302, Eureka 200 : toute reponse HTTP prouve que le port ecoute.
            if ($_.Exception.Response) { return $true }
        }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Test-Url($url) {
    try { Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 | Out-Null; return $true }
    catch { if ($_.Exception.Response) { return $true }; return $false }
}

Write-Host "=== Demarrage de la stack BIAT Flow ===" -ForegroundColor White

# --- 1. IP LAN ---------------------------------------------------------------------------
Write-Step 1 "Alignement de l'IP LAN"
& (Join-Path $PSScriptRoot 'sync-lan-ip.ps1')
if ($LASTEXITCODE -ne 0) { throw "sync-lan-ip.ps1 a echoue (code $LASTEXITCODE)." }

# --- 2. Infrastructure -------------------------------------------------------------------
Write-Step 2 "Infrastructure Docker"
Push-Location $root
try {
    docker compose up -d postgres-users postgres-tickets postgres-pipelines `
        postgres-notifications postgres-audit postgres-keycloak keycloak rabbitmq ngrok
    if ($LASTEXITCODE -ne 0) { throw "docker compose (infrastructure) a echoue." }
} finally { Pop-Location }

Write-Info "Attente de Keycloak (peut prendre une minute au premier demarrage)..."
if (Wait-Url 'http://localhost:9090/' 180 'keycloak') { Write-Ok "Keycloak" }
else { Write-Warn2 "Keycloak ne repond pas encore - la connexion a l'app echouera tant qu'il n'est pas pret." }

# --- 3. Services natifs ------------------------------------------------------------------
Write-Step 3 "discovery-server et api-gateway (hors Docker)"
if ($SkipNative) {
    Write-Info "-SkipNative : demarrage delegue a IntelliJ, verification seule."
} else {
    $jdk = $env:JAVA_HOME
    if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
        # JAVA_HOME n'est pas defini sur cette machine (le JDK vient d'IntelliJ). On explore des
        # dossiers precis plutot que des jokers larges : un motif du type C:\Users\*\.jdks\*
        # force un parcours de tous les profils utilisateurs et peut bloquer plusieurs minutes.
        $jdk = $null
        $roots = @(
            (Join-Path $env:USERPROFILE '.jdks'),
            'C:\Program Files\Java',
            'C:\Program Files\Eclipse Adoptium',
            'C:\actions-runner\_work\_tool\Java_Temurin-Hotspot_jdk'
        )
        foreach ($r in $roots) {
            if (-not (Test-Path $r)) { continue }
            $found = Get-ChildItem $r -Directory -ErrorAction SilentlyContinue |
                     Where-Object { $_.Name -match '17' } |
                     ForEach-Object {
                         # Le JDK du runner ajoute un niveau d'architecture (…\17.0.20-8\x64).
                         @($_.FullName, (Join-Path $_.FullName 'x64'))
                     } |
                     Where-Object { Test-Path (Join-Path $_ 'bin\java.exe') } |
                     Select-Object -First 1
            if ($found) { $jdk = $found; break }
        }
    }
    if (-not $jdk) { throw "Aucun JDK 17 trouve. Definissez JAVA_HOME, ou utilisez -SkipNative et lancez les deux services depuis IntelliJ." }
    Write-Info "JDK : $jdk"

    foreach ($svc in 'discovery-server', 'api-gateway') {
        $port = if ($svc -eq 'discovery-server') { 8761 } else { 8080 }
        # Parentheses obligatoires : sans elles, "-or" serait passe comme argument a Test-Url
        # au lieu d'etre l'operateur logique, et la condition serait toujours vraie.
        if ((Test-Url "http://localhost:$port/actuator/health") -or (Test-Url "http://localhost:$port/")) {
            Write-Info "$svc repond deja sur $port, relance inutile."
            continue
        }
        Write-Info "Lancement de $svc..."
        # Fenetre distincte et minimisee : les logs restent consultables, et fermer la fenetre
        # suffit a arreter le service.
        Start-Process -FilePath 'powershell.exe' -WindowStyle Minimized -ArgumentList @(
            '-NoExit', '-NoProfile', '-Command',
            "`$env:JAVA_HOME='$jdk'; Set-Location '$root\backend'; .\mvnw.cmd -pl $svc spring-boot:run"
        )
    }
}

Write-Info "Attente de discovery-server (Eureka)..."
if (Wait-Url 'http://localhost:8761/' 180 'eureka') { Write-Ok "discovery-server" }
else { Write-Warn2 "discovery-server ne repond pas : les microservices ne pourront pas s'enregistrer." }

Write-Info "Attente de api-gateway..."
if (Wait-Url 'http://localhost:8080/actuator/health' 180 'gateway') { Write-Ok "api-gateway" }
else { Write-Warn2 "api-gateway ne repond pas : le frontend recevra des erreurs." }

# --- 4. Microservices --------------------------------------------------------------------
Write-Step 4 "Microservices metier (Docker)"
Push-Location $root
try {
    docker compose up -d user-service ticket-service pipeline-service notification-service audit-service
    if ($LASTEXITCODE -ne 0) {
        # Cas deja rencontre : une dependance mise trop longtemps a devenir healthy laisse les
        # conteneurs en etat Created. Relancer suffit, l'infrastructure etant prete cette fois.
        Write-Warn2 "Premier essai en echec (dependance lente a devenir healthy). Nouvelle tentative..."
        Start-Sleep -Seconds 10
        docker compose up -d user-service ticket-service pipeline-service notification-service audit-service
        if ($LASTEXITCODE -ne 0) { throw "docker compose (microservices) a echoue deux fois." }
    }
} finally { Pop-Location }

$services = [ordered]@{
    'user-service'         = 8081
    'ticket-service'       = 8082
    'pipeline-service'     = 8083
    'notification-service' = 8084
    'audit-service'        = 8085
}
foreach ($name in $services.Keys) {
    $port = $services[$name]
    if (Wait-Url "http://localhost:$port/actuator/health" 180 $name) { Write-Ok "$name ($port)" }
    else { Write-Warn2 "$name ($port) ne repond pas." }
}

# --- 5. Runner ---------------------------------------------------------------------------
Write-Step 5 "Runner GitHub Actions"
if ($SkipRunner) {
    Write-Info "-SkipRunner : ignore."
} elseif (Get-Process -Name 'Runner.Listener' -ErrorAction SilentlyContinue) {
    Write-Ok "Deja en cours d'execution."
} elseif (-not (Test-Path (Join-Path $runnerDir 'run.cmd'))) {
    Write-Warn2 "$runnerDir introuvable - runner non demarre (voir docs/runner-setup.md)."
} else {
    Start-Process -FilePath (Join-Path $runnerDir 'run.cmd') -WorkingDirectory $runnerDir -WindowStyle Minimized
    Start-Sleep -Seconds 8
    if (Get-Process -Name 'Runner.Listener' -ErrorAction SilentlyContinue) { Write-Ok "Demarre." }
    else { Write-Warn2 "Le runner n'a pas demarre - les deploiements resteront en file d'attente." }
}

# --- Bilan -------------------------------------------------------------------------------
Write-Host "`n=== Bilan ===" -ForegroundColor White
$checks = [ordered]@{
    'Keycloak (9090)'          = 'http://localhost:9090/'
    'discovery-server (8761)'  = 'http://localhost:8761/'
    'api-gateway (8080)'       = 'http://localhost:8080/actuator/health'
    'user-service (8081)'      = 'http://localhost:8081/actuator/health'
    'ticket-service (8082)'    = 'http://localhost:8082/actuator/health'
    'pipeline-service (8083)'  = 'http://localhost:8083/actuator/health'
    'notification-service (8084)' = 'http://localhost:8084/actuator/health'
    'audit-service (8085)'     = 'http://localhost:8085/actuator/health'
}
$allOk = $true
foreach ($label in $checks.Keys) {
    if (Test-Url $checks[$label]) { Write-Host ("  [OK]   " + $label) -ForegroundColor Green }
    else { Write-Host ("  [KO]   " + $label) -ForegroundColor Red; $allOk = $false }
}
if (Get-Process -Name 'Runner.Listener' -ErrorAction SilentlyContinue) {
    Write-Host "  [OK]   Runner GitHub Actions" -ForegroundColor Green
} else {
    Write-Host "  [KO]   Runner GitHub Actions" -ForegroundColor Red
    if (-not $SkipRunner) { $allOk = $false }
}

Write-Host ""
if ($allOk) {
    Write-Host "Tout est pret. Lancez le frontend :  cd frontend ; npm run dev" -ForegroundColor Green
    Write-Host "Puis ouvrez http://localhost:5173"
} else {
    Write-Host "Certains composants ne repondent pas (voir [KO] ci-dessus)." -ForegroundColor Yellow
    Write-Host "Relancer ce script est sans risque : ce qui tourne deja est conserve."
}
