<#
.SYNOPSIS
    Aligne EUREKA_INSTANCE_HOSTNAME (.env) sur l'IP LAN actuelle de la machine.

.DESCRIPTION
    Les microservices conteneurisés s'annoncent auprès d'Eureka à l'adresse
    EUREKA_INSTANCE_HOSTNAME. Elle doit être l'IP LAN de la machine : leur hostname Docker
    interne serait injoignable depuis discovery-server et api-gateway, qui tournent nativement
    sur l'hôte.

    Cette IP étant attribuée par DHCP, elle change (redémarrage du routeur, changement de
    réseau). Quand elle change, les conteneurs continuent de s'annoncer à l'ancienne adresse :
    la gateway les appelle dans le vide, les requêtes partent en timeout et l'application
    n'affiche plus rien. Ce script rétablit la cohérence.

    À lancer avant de démarrer la stack, ou dès que l'application devient anormalement lente.

.PARAMETER Check
    N'écrit rien : signale seulement si .env est désynchronisé (code de sortie 1 si c'est le
    cas). Pratique en pré-vérification.

.EXAMPLE
    .\scripts\sync-lan-ip.ps1
    .\scripts\sync-lan-ip.ps1 -Check
#>
[CmdletBinding()]
param(
    [switch]$Check
)

$ErrorActionPreference = 'Stop'

$envFile = Join-Path (Split-Path -Parent $PSScriptRoot) '.env'
if (-not (Test-Path $envFile)) {
    Write-Error "Fichier introuvable : $envFile — créez-le depuis .env.example."
    exit 2
}

# On retient l'interface qui porte la passerelle par défaut, et non la première IPv4 venue :
# une machine de dev expose souvent des cartes virtuelles (WSL, Hyper-V, VirtualBox, VMware)
# dont les adresses sont injoignables depuis les conteneurs.
$config = Get-NetIPConfiguration |
    Where-Object { $null -ne $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } |
    Select-Object -First 1

# @(...) force le tableau : une interface peut porter plusieurs IPv4, et l'accès direct à
# .IPAddress renverrait alors un tableau au lieu d'une chaîne.
$lanIp = if ($config) { @($config.IPv4Address)[0].IPAddress } else { $null }

if (-not $lanIp) {
    Write-Error "Aucune interface active avec passerelle par défaut. Machine connectée au réseau ?"
    exit 2
}

# -Encoding UTF8 explicite : sans lui, PowerShell 5.1 lit le fichier en ANSI (cp1252) et les
# accents des commentaires ressortent en mojibake une fois réécrits.
$content = Get-Content $envFile -Raw -Encoding UTF8
$pattern = '(?m)^EUREKA_INSTANCE_HOSTNAME=(.*)$'
$match = [regex]::Match($content, $pattern)

if (-not $match.Success) {
    Write-Error "EUREKA_INSTANCE_HOSTNAME absent de $envFile — voir .env.example."
    exit 2
}

$current = $match.Groups[1].Value.Trim()

if ($current -eq $lanIp) {
    Write-Host "EUREKA_INSTANCE_HOSTNAME deja aligne sur $lanIp - rien a faire." -ForegroundColor Green
    exit 0
}

if ($Check) {
    Write-Warning "Desynchronise : .env indique '$current', l'IP LAN actuelle est '$lanIp'."
    Write-Host "Corrigez avec : .\scripts\sync-lan-ip.ps1" -ForegroundColor Yellow
    exit 1
}

# WriteAllText avec UTF8Encoding($false) plutôt que Set-Content -Encoding utf8 : ce dernier
# ajoute un BOM en PowerShell 5.1. Un BOM en tête de .env serait lu par docker compose comme
# faisant partie du nom de la première variable, qui deviendrait alors introuvable.
$updated = [regex]::Replace($content, $pattern, "EUREKA_INSTANCE_HOSTNAME=$lanIp")
[System.IO.File]::WriteAllText($envFile, $updated, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "EUREKA_INSTANCE_HOSTNAME : $current -> $lanIp" -ForegroundColor Green
Write-Host ""
Write-Host "Recreez les conteneurs applicatifs pour appliquer le changement :" -ForegroundColor Yellow
Write-Host "  docker compose up -d --force-recreate user-service ticket-service pipeline-service notification-service audit-service"
