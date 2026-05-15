param(
    [Parameter(Mandatory = $true)]
    [string]$VideoPath,

    [string]$GatewayBaseUrl = "http://localhost:18080",
    [string]$SearchText = "视频内容",
    [string]$ApiKey = $env:AI_SEARCH_API_KEY,
    [string]$Roles = "ADMIN",
    [int]$TimeoutSeconds = 900,
    [int]$PollSeconds = 5
)

$ErrorActionPreference = "Stop"

function New-Headers([bool]$Admin = $false) {
    $headers = @{}
    if ($ApiKey) {
        $headers["X-AI-Search-Api-Key"] = $ApiKey
    }
    if ($Admin -and $Roles) {
        $headers["X-AI-Search-Roles"] = $Roles
    }
    return $headers
}

function Invoke-Json([string]$Method, [string]$Url, $Body = $null, [bool]$Admin = $false) {
    $params = @{
        Method = $Method
        Uri = $Url
        Headers = New-Headers $Admin
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 12)
    }
    $response = Invoke-RestMethod @params
    if (-not $response.success) {
        throw "API request failed: $Method $Url - $($response.message)"
    }
    return $response.data
}

if (-not (Test-Path -LiteralPath $VideoPath)) {
    throw "VideoPath does not exist: $VideoPath"
}

$file = Get-Item -LiteralPath $VideoPath
$contentType = "video/mp4"
if ($file.Extension -eq ".mov") {
    $contentType = "video/quicktime"
} elseif ($file.Extension -eq ".mkv") {
    $contentType = "video/x-matroska"
}

Write-Host "Checking gateway health..."
Invoke-RestMethod -Uri "$GatewayBaseUrl/actuator/health" -Method Get | Out-Null

Write-Host "Creating upload session for $($file.Name)..."
$upload = Invoke-Json "POST" "$GatewayBaseUrl/api/videos/uploads" @{
    fileName = $file.Name
    fileSize = $file.Length
    contentType = $contentType
    title = $file.BaseName
}

Write-Host "Uploading to MinIO presigned URL..."
$putResponse = Invoke-WebRequest -Method Put -Uri $upload.uploadUrl -InFile $file.FullName -ContentType $contentType
$etag = $putResponse.Headers["ETag"]
if ($etag -is [array]) {
    $etag = $etag[0]
}

Write-Host "Completing upload, videoId=$($upload.videoId)..."
$complete = Invoke-Json "POST" "$GatewayBaseUrl/api/videos/$($upload.videoId)/complete" @{
    objectETag = $etag
    fileSize = $file.Length
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds $PollSeconds
    $status = Invoke-Json "GET" "$GatewayBaseUrl/api/workflows/video-indexing/videos/$($complete.videoId)"
    $failed = @($status.stages | Where-Object { $_.status -eq "FAILED" })
    if ($failed.Count -gt 0) {
        $failed | ConvertTo-Json -Depth 6
        throw "Video workflow failed for $($complete.videoId)"
    }
    $done = @($status.stages).Count -gt 0 -and @($status.stages | Where-Object { $_.status -ne "SUCCEEDED" }).Count -eq 0
    Write-Host "Workflow progress: $(@($status.stages | Where-Object { $_.status -eq "SUCCEEDED" }).Count)/$(@($status.stages).Count) stages, segments=$(@($status.segments).Count)"
} while (-not $done -and (Get-Date) -lt $deadline)

if (-not $done) {
    throw "Timed out waiting for workflow completion: $($complete.videoId)"
}

Write-Host "Fetching segment artifacts..."
$segments = Invoke-Json "GET" "$GatewayBaseUrl/api/workflows/video-indexing/videos/$($complete.videoId)/segments/artifacts" $null $true

Write-Host "Running search smoke query..."
$search = Invoke-Json "POST" "$GatewayBaseUrl/api/search" @{
    text = $SearchText
    topK = 5
    withAnalysis = $false
}

$result = [ordered]@{
    videoId = $complete.videoId
    objectKey = $complete.objectKey
    stageCount = @($status.stages).Count
    segmentCount = @($status.segments).Count
    segmentArtifactKeys = @($segments.PSObject.Properties.Name)
    searchRequestId = $search.requestId
    searchResultCount = @($search.results).Count
}

$result | ConvertTo-Json -Depth 8
