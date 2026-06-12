# Rebuild PR branches #2-#11 from current master (after PR #1)
$ErrorActionPreference = "Continue"
Set-Location (Split-Path $PSScriptRoot -Parent)

function Remove-BuildArtifactsFromIndex {
    $tracked = git ls-files | Where-Object { $_ -match '/target/' -or $_ -eq '.env' }
    if (-not $tracked) { return }
    foreach ($f in $tracked) { git rm -rf --cached $f 2>$null | Out-Null }
    git commit --amend --no-edit
}

function Invoke-CherryPicks {
    param([string[]]$CommitList, [switch]$SkipEnvFile)
    if ($SkipEnvFile -and (Test-Path ".env")) { Move-Item .env .env.local.bak -Force }
    foreach ($c in $CommitList) {
        git cherry-pick $c
        if ($LASTEXITCODE -ne 0) { throw "cherry-pick failed: $c" }
        Remove-BuildArtifactsFromIndex
    }
    if ($SkipEnvFile -and (Test-Path ".env.local.bak")) { Move-Item .env.local.bak .env -Force }
}

function Invoke-Pr {
    param([int]$N, [string]$Branch, [string[]]$Commits, [switch]$SkipEnvFile)
    Write-Host "=== PR #$N $Branch ===" -ForegroundColor Cyan
    git checkout -f master
    git pull origin master
    git clean -fdx -e .env -e .codegraph -e scripts/backfill_prs.ps1 -e scripts/publish_github_prs.ps1 2>$null | Out-Null
    git branch -D $Branch 2>$null | Out-Null
    git push origin --delete $Branch 2>$null | Out-Null
    git checkout -b $Branch
    if ($SkipEnvFile) { Invoke-CherryPicks -CommitList $Commits -SkipEnvFile }
    else { Invoke-CherryPicks -CommitList $Commits }
    Remove-BuildArtifactsFromIndex
    git push -u origin $Branch --force
    git checkout -f master
    git merge --no-ff $Branch -m "Merge pull request #$N from whitequeen306/$Branch"
    git push origin master
}

Invoke-Pr 2 "feat/02-common-dao" @("344a903","7c778b2")
Invoke-Pr 3 "feat/03-security-storage" @("e9fb9e4","d378755")
Invoke-Pr 4 "feat/04-ai-module" @("1d7467a")
Invoke-Pr 5 "feat/05-service-layer" @("7eed024")
Invoke-Pr 6 "feat/06-web-app" @("eeb55e4") -SkipEnvFile
Invoke-Pr 7 "feat/07-frontend" @("5b4d1b2")
Invoke-Pr 8 "feat/08-docker-deploy" @("2dd72cc","5888b94","2d6fb88","e36783b","7d50b50","1adc665","d235b56","47f95a7","254e8c4","e39abbb","a2acd19","a314229","421213c")
Invoke-Pr 9 "fix/09-integration" @("c12224b","0e446e9","b5ffb31","2964740","607aafe","5c5d09b","8ff0bec","2f8d301")
Invoke-Pr 10 "feat/10-vision-pipeline" @("25e1cfb")
Invoke-Pr 11 "docs/11-readme-config" @("0c0f3dc","96f9d40","fac28ee","d28c222","c846441","f4a4d56")

Write-Host "Done. Run gh auth login then scripts/publish_github_prs.ps1" -ForegroundColor Green
