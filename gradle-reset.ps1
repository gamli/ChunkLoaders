
# Self-elevate the script if required 
if (-Not ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] 'Administrator')) {
 if ([int](Get-CimInstance -Class Win32_OperatingSystem | Select-Object -ExpandProperty BuildNumber) -ge 6000) {
  $CommandLine = "-Command Set-Location -Path `"" + (Get-Item $MyInvocation.MyCommand.Path).Directory.FullName + "`"; & `"" + $MyInvocation.MyCommand.Path + "`" " + $MyInvocation.UnboundArguments
  Start-Process -FilePath PowerShell.exe -Verb Runas -ArgumentList $CommandLine
  Exit
 }
}

$Gradle = "${pwd}\.gradle"
if (Test-Path -Path $Gradle) {
    echo "Removing $Gradle ..."
    rm -r -fo $Gradle
    echo "      done."
} else {
    echo "$Gradle does not exist"
}

$Build = "${pwd}\build"
if (Test-Path -Path $Build) {
    echo "Removing $Build ..."
    rm -r -fo $Build
    echo "      done."
} else {
    echo "$Build does not exist"
}

$Run = "${pwd}\run"
if (Test-Path -Path $Run) {
    echo "Removing $Run ..."
    rm -r -fo $Run
    echo "      done."
} else {
    echo "$Run does not exist"
}

$Caches = "$env:USERPROFILE\.gradle\caches"
if (Test-Path -Path $Caches) {
    echo "Removing $Caches ..."
    rm -r -fo $Caches
    echo "      done."
} else {
    echo "$Caches does not exist"
}

pause